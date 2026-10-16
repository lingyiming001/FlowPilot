package com.flowpilot.agent.llm;

import com.flowpilot.agent.config.ModelSettingsService;
import com.flowpilot.agent.context.AgentContext;
import com.flowpilot.agent.mapper.AgentMapper;
import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.common.util.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Primary
public class RoutedLlmClient implements LlmClient {
  private final ModelSettingsService settings;
  private final AgentMapper db;
  private final Crypto crypto;
  private final TransactionTemplate tx;
  private final FakeLlmClient fake;
  private final SpringAiLlmClient real;
  private final String envMode, url, key, name;

  public RoutedLlmClient(
      ModelSettingsService settings,
      AgentMapper db,
      Crypto crypto,
      TransactionTemplate tx,
      FakeLlmClient fake,
      SpringAiLlmClient real,
      @Value("${flowpilot.llm-mode:fake}") String mode,
      @Value("${flowpilot.llm-base-url}") String url,
      @Value("${flowpilot.llm-api-key}") String key,
      @Value("${flowpilot.llm-model}") String name) {
    this.settings = settings;
    this.db = db;
    this.crypto = crypto;
    this.tx = tx;
    this.fake = fake;
    this.real = real;
    this.envMode = mode;
    this.url = url;
    this.key = key;
    this.name = name;
  }

  public String mode() {
    return envMode.toUpperCase();
  }

  public String mode(String team) {
    return settings.managed(team) ? "MANAGED" : mode();
  }

  private long n(Map<String, Object> r, String key) {
    return ((Number) r.get(key)).longValue();
  }

  private List<Map<String, Object>> ledger(AgentContext c) {
    return db.rows(
        "SELECT * FROM llm_token_ledger WHERE team_id=? AND workflow_id=? AND round_number=?",
        c.teamId(),
        c.workflowId(),
        c.round());
  }

  private Reply cached(Map<String, Object> row) {
    if (row.get("reply_cipher") != null)
      return Json.read(crypto.decrypt(row.get("reply_cipher").toString()), Reply.class);
    if (!"RELEASED".equals(row.get("status")))
      throw BusinessException.conflict("本轮模型调用正在运行或结果待对账，请在模型与用量中核对后重试");
    return null;
  }

  public Reply call(AgentContext c) {
    var existing = ledger(c);
    if (!existing.isEmpty()) {
      var reply = cached(existing.getFirst());
      if (reply != null) return reply;
    }
    boolean managed = settings.managed(c.teamId());
    Map<String, Object> model =
        managed
            ? settings.resolveManaged(
                c.teamId(), c.round() == 0 ? "INTENT_ANALYSIS" : "TOOL_REASONING")
            : null;
    if (managed ? "FAKE".equals(model.get("type")) : !"real".equalsIgnoreCase(envMode))
      return fake.call(c);
    long input = real.inputBound(c);
    int output = managed ? (int) n(model, "max_output_tokens") : 4096;
    long maxInput = managed ? n(model, "max_input_tokens") : 100000,
        context = managed ? n(model, "context_window") : 128000;
    if (input > maxInput || input + output > context)
      throw BusinessException.invalid("请求超过模型输入或上下文上限（按保守字节上界估算），请缩减邮件或历史");
    String modelId = managed ? model.get("id").toString() : "ENVIRONMENT",
        month = ModelSettingsService.month();
    long reserve = input + output;
    var config =
        new SpringAiLlmClient.Config(
            managed ? model.get("base_url").toString() : url,
            managed ? model.get("chat_path").toString() : "/v1/chat/completions",
            managed ? crypto.decrypt(model.get("api_key_cipher").toString()) : key,
            managed ? model.get("model_name").toString() : name,
            managed
                ? (model.get("temperature") == null
                    ? null
                    : ((Number) model.get("temperature")).doubleValue())
                : 0.1,
            output,
            managed ? model.get("output_parameter").toString() : "MAX_TOKENS",
            managed ? (int) n(model, "timeout_seconds") : 25);
    String id =
        tx.execute(
            t -> {
              settings.lock(c.teamId());
              var old = ledger(c);
              if (!old.isEmpty()) {
                var r = cached(old.getFirst());
                if (r != null) return null;
              }
              var s = settings.settings(c.teamId());
              db.update(
                  "INSERT IGNORE INTO llm_monthly_usage(team_id,month_key) VALUES(?,?)",
                  c.teamId(),
                  month);
              var usage =
                  db.one(
                      "SELECT * FROM llm_monthly_usage WHERE team_id=? AND month_key=? FOR UPDATE",
                      c.teamId(),
                      month);
              if (n(usage, "used_tokens") + n(usage, "reserved_tokens") + reserve
                  > n(s, "monthly_token_limit"))
                throw BusinessException.invalid("本月 Token 额度不足（包含运行中预留量）");
              long spent =
                  db.count(
                      "SELECT COALESCE(SUM(charged_tokens+CASE WHEN status IN ('RESERVED','UNCERTAIN') THEN reserved_tokens ELSE 0 END),0) FROM llm_token_ledger WHERE team_id=? AND workflow_id=?",
                      c.teamId(),
                      c.workflowId());
              if (spent + reserve > n(s, "workflow_token_limit"))
                throw BusinessException.invalid("工作流 Token 额度不足");
              db.update(
                  "UPDATE llm_monthly_usage SET reserved_tokens=reserved_tokens+? WHERE team_id=? AND month_key=?",
                  reserve,
                  c.teamId(),
                  month);
              String result = old.isEmpty() ? Json.id() : old.getFirst().get("id").toString();
              if (old.isEmpty())
                db.update(
                    "INSERT INTO llm_token_ledger(id,team_id,month_key,workflow_id,round_number,model_id,reserved_tokens,status) VALUES(?,?,?,?,?,?,?,'RESERVED')",
                    result,
                    c.teamId(),
                    month,
                    c.workflowId(),
                    c.round(),
                    modelId,
                    reserve);
              else
                db.update(
                    "UPDATE llm_token_ledger SET month_key=?,model_id=?,reserved_tokens=?,charged_tokens=0,input_tokens=NULL,output_tokens=NULL,estimated=FALSE,status='RESERVED',created_at=UTC_TIMESTAMP(6) WHERE id=?",
                    month,
                    modelId,
                    reserve,
                    result);
              return result;
            });
    if (id == null) return cached(ledger(c).getFirst());
    try {
      var result = real.call(c, config);
      boolean estimated = result.total() == null || result.total() <= 0;
      long actual =
          estimated
              ? reserve
              : Math.max(
                  result.total(),
                  (result.input() == null ? 0 : result.input())
                      + (result.output() == null ? 0 : result.output()));
      tx.executeWithoutResult(
          t -> {
            settings.lock(c.teamId());
            var l = db.one("SELECT status FROM llm_token_ledger WHERE id=? FOR UPDATE", id);
            if (!"RESERVED".equals(l.get("status")))
              throw BusinessException.conflict("调用已被对账，需人工核查");
            db.update(
                "UPDATE llm_monthly_usage SET reserved_tokens=reserved_tokens-?,used_tokens=used_tokens+? WHERE team_id=? AND month_key=?",
                reserve,
                actual,
                c.teamId(),
                month);
            db.update(
                "UPDATE llm_token_ledger SET status='SETTLED',input_tokens=?,output_tokens=?,charged_tokens=?,estimated=?,reply_cipher=? WHERE id=?",
                result.input(),
                result.output(),
                actual,
                estimated,
                crypto.encrypt(Json.write(result.reply())),
                id);
          });
      return result.reply();
    } catch (RuntimeException e) {
      db.update(
          "UPDATE llm_token_ledger SET status='UNCERTAIN' WHERE id=? AND status='RESERVED'", id);
      throw new BusinessException(
          "LLM_UNCERTAIN",
          "模型调用未能确认完成；额度保持预留，请核对供应商用量后在模型与用量中对账",
          org.springframework.http.HttpStatus.BAD_GATEWAY);
    }
  }
}
