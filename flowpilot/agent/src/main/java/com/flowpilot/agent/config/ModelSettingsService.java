package com.flowpilot.agent.config;

import com.flowpilot.agent.mapper.AgentMapper;
import com.flowpilot.audit.service.AuditService;
import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.common.util.*;
import com.flowpilot.security.service.AuthService;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ModelSettingsService {
  public record Provider(
      @NotBlank @Size(max = 100) String name,
      @Pattern(regexp = "OPENAI_COMPATIBLE|FAKE") @NotNull String type,
      @NotNull @Size(max = 500) String baseUrl,
      @NotBlank @Size(max = 200) String chatPath,
      @Size(max = 4000) String apiKey,
      boolean clearApiKey,
      @Min(5) @Max(45) int timeoutSeconds,
      boolean enabled,
      @Min(0) int version) {}

  public record Model(
      @NotBlank String providerId,
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(max = 200) String modelName,
      @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
      @Min(256) @Max(2000000) int contextWindow,
      @Min(128) @Max(1000000) int maxInputTokens,
      @Min(128) @Max(1000000) int maxOutputTokens,
      @NotNull @Pattern(regexp = "MAX_TOKENS|MAX_COMPLETION_TOKENS") String outputParameter,
      boolean enabled,
      @Min(0) int version) {}

  public record Route(
      @NotNull @Pattern(regexp = "INTENT_ANALYSIS|TOOL_REASONING|DEFAULT") String routeKey,
      @NotBlank String modelId,
      @Min(0) @Max(10000) int priority,
      boolean enabled,
      @Min(0) int version) {}

  public record Settings(
      boolean routingEnabled,
      @Min(0) @Max(1000000000000L) long monthlyTokenLimit,
      @Min(256) @Max(1000000000) long workflowTokenLimit,
      @Min(0) int version) {}

  private final AgentMapper db;
  private final AuthService auth;
  private final Crypto crypto;
  private final AuditService audit;
  private final TransactionTemplate tx;

  public ModelSettingsService(
      AgentMapper db, AuthService auth, Crypto crypto, AuditService audit, TransactionTemplate tx) {
    this.db = db;
    this.auth = auth;
    this.crypto = crypto;
    this.audit = audit;
    this.tx = tx;
  }

  public static String month() {
    return YearMonth.now(ZoneId.of("Asia/Shanghai")).toString();
  }

  public Map<String, Object> settings(String team) {
    db.update("INSERT IGNORE INTO llm_settings(team_id) VALUES(?)", team);
    return db.one("SELECT * FROM llm_settings WHERE team_id=?", team);
  }

  public void lock(String team) {
    settings(team);
    db.one("SELECT team_id FROM llm_settings WHERE team_id=? FOR UPDATE", team);
  }

  private String team() {
    auth.require("models:manage");
    return auth.current().teamId();
  }

  private void changed(String team, String what) {
    audit.record(team, null, auth.current().id(), "MODEL_CONFIG_CHANGED", what);
  }

  public Map<String, Object> overview() {
    String team = team();
    var s = settings(team);
    var accounts =
        db.rows(
            "SELECT * FROM llm_monthly_usage WHERE team_id=? ORDER BY month_key DESC LIMIT 12",
            team);
    var account =
        db.rows("SELECT * FROM llm_monthly_usage WHERE team_id=? AND month_key=?", team, month());
    return Map.of(
        "settings",
        s,
        "month",
        month(),
        "usage",
        account.isEmpty() ? Map.of("used_tokens", 0, "reserved_tokens", 0) : account.getFirst(),
        "history",
        accounts,
        "providers",
        db.rows(
            "SELECT id,name,type,base_url,chat_path,(api_key_cipher IS NOT NULL) AS has_api_key,timeout_seconds,enabled,version FROM llm_provider WHERE team_id=? ORDER BY name,id",
            team),
        "models",
        db.rows("SELECT * FROM llm_model WHERE team_id=? ORDER BY name,id", team),
        "routes",
        db.rows("SELECT * FROM llm_route WHERE team_id=? ORDER BY route_key,priority,id", team),
        "ledger",
        db.rows(
            "SELECT id,month_key,workflow_id,round_number,model_id,reserved_tokens,input_tokens,output_tokens,charged_tokens,estimated,status,created_at FROM llm_token_ledger WHERE team_id=? ORDER BY created_at DESC LIMIT 100",
            team));
  }

  private void optimistic(int n) {
    if (n != 1) throw BusinessException.conflict("配置已更新，请刷新后重试");
  }

  public String provider(String id, Provider p) {
    String team = team();
    if (!p.chatPath().startsWith("/") || p.chatPath().contains("?") || p.chatPath().contains("#"))
      throw BusinessException.invalid("接口路径必须以 / 开头，不能包含查询参数");
    if (p.type().equals("OPENAI_COMPATIBLE")) {
      try {
        URI u = URI.create(p.baseUrl());
        if (u.getScheme() == null || !List.of("https", "http").contains(u.getScheme())
            || u.getHost() == null
            || u.getUserInfo() != null
            || u.getQuery() != null
            || u.getFragment() != null) throw new IllegalArgumentException();
      } catch (IllegalArgumentException e) {
        throw BusinessException.invalid("请填写有效的 HTTP(S) Base URL，不要包含密钥");
      }
    }
    return tx.execute(
        s -> {
          lock(team);
          String key =
              p.apiKey() != null && !p.apiKey().isBlank()
                  ? crypto.encrypt(p.apiKey().trim())
                  : null;
          String result = id == null ? Json.id() : id;
          if (id == null) {
            db.update(
                "INSERT INTO llm_provider(id,team_id,name,type,base_url,chat_path,api_key_cipher,timeout_seconds,enabled) VALUES(?,?,?,?,?,?,?,?,?)",
                result,
                team,
                p.name().trim(),
                p.type(),
                p.baseUrl().trim(),
                p.chatPath(),
                key,
                p.timeoutSeconds(),
                p.enabled());
          } else {
            var old =
                db.one(
                    "SELECT api_key_cipher FROM llm_provider WHERE id=? AND team_id=?", id, team);
            if (key == null && !p.clearApiKey()) key = (String) old.get("api_key_cipher");
            optimistic(
                db.update(
                    "UPDATE llm_provider SET name=?,type=?,base_url=?,chat_path=?,api_key_cipher=?,timeout_seconds=?,enabled=?,version=version+1 WHERE id=? AND team_id=? AND version=?",
                    p.name().trim(),
                    p.type(),
                    p.baseUrl().trim(),
                    p.chatPath(),
                    key,
                    p.timeoutSeconds(),
                    p.enabled(),
                    id,
                    team,
                    p.version()));
          }
          changed(team, "供应商配置 " + result);
          return result;
        });
  }

  public String model(String id, Model m) {
    String team = team();
    if ((long) m.maxInputTokens() + m.maxOutputTokens() > m.contextWindow())
      throw BusinessException.invalid("输入与输出上限之和不能超过上下文窗口");
    return tx.execute(
        s -> {
          lock(team);
          db.one("SELECT id FROM llm_provider WHERE id=? AND team_id=?", m.providerId(), team);
          String result = id == null ? Json.id() : id;
          if (id == null)
            db.update(
                "INSERT INTO llm_model(id,team_id,provider_id,name,model_name,temperature,context_window,max_input_tokens,max_output_tokens,output_parameter,enabled) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                result,
                team,
                m.providerId(),
                m.name().trim(),
                m.modelName().trim(),
                m.temperature(),
                m.contextWindow(),
                m.maxInputTokens(),
                m.maxOutputTokens(),
                m.outputParameter(),
                m.enabled());
          else
            optimistic(
                db.update(
                    "UPDATE llm_model SET provider_id=?,name=?,model_name=?,temperature=?,context_window=?,max_input_tokens=?,max_output_tokens=?,output_parameter=?,enabled=?,version=version+1 WHERE id=? AND team_id=? AND version=?",
                    m.providerId(),
                    m.name().trim(),
                    m.modelName().trim(),
                    m.temperature(),
                    m.contextWindow(),
                    m.maxInputTokens(),
                    m.maxOutputTokens(),
                    m.outputParameter(),
                    m.enabled(),
                    id,
                    team,
                    m.version()));
          changed(team, "模型配置 " + result);
          return result;
        });
  }

  public String route(String id, Route r) {
    String team = team();
    return tx.execute(
        s -> {
          lock(team);
          db.one("SELECT id FROM llm_model WHERE id=? AND team_id=?", r.modelId(), team);
          String result = id == null ? Json.id() : id;
          if (id == null)
            db.update(
                "INSERT INTO llm_route(id,team_id,route_key,model_id,priority,enabled) VALUES(?,?,?,?,?,?)",
                result,
                team,
                r.routeKey(),
                r.modelId(),
                r.priority(),
                r.enabled());
          else
            optimistic(
                db.update(
                    "UPDATE llm_route SET route_key=?,model_id=?,priority=?,enabled=?,version=version+1 WHERE id=? AND team_id=? AND version=?",
                    r.routeKey(),
                    r.modelId(),
                    r.priority(),
                    r.enabled(),
                    id,
                    team,
                    r.version()));
          changed(team, "模型路由 " + result);
          return result;
        });
  }

  public void save(Settings s) {
    String team = team();
    tx.executeWithoutResult(
        t -> {
          lock(team);
          if (s.routingEnabled()) {
            resolveManaged(team, "INTENT_ANALYSIS");
            resolveManaged(team, "TOOL_REASONING");
          }
          optimistic(
              db.update(
                  "UPDATE llm_settings SET routing_enabled=?,monthly_token_limit=?,workflow_token_limit=?,version=version+1 WHERE team_id=? AND version=?",
                  s.routingEnabled(),
                  s.monthlyTokenLimit(),
                  s.workflowTokenLimit(),
                  team,
                  s.version()));
          changed(team, "月度额度=" + s.monthlyTokenLimit() + "；托管路由=" + s.routingEnabled());
        });
  }

  public boolean managed(String team) {
    return Boolean.TRUE.equals(settings(team).get("routing_enabled"));
  }

  public Map<String, Object> resolveManaged(String team, String key) {
    var rows =
        db.rows(
            "SELECT m.*,p.type,p.base_url,p.chat_path,p.api_key_cipher,p.timeout_seconds,p.name AS provider_name,r.route_key FROM llm_route r JOIN llm_model m ON m.id=r.model_id AND m.team_id=r.team_id JOIN llm_provider p ON p.id=m.provider_id AND p.team_id=m.team_id WHERE r.team_id=? AND r.enabled=TRUE AND m.enabled=TRUE AND p.enabled=TRUE AND (p.type='FAKE' OR p.api_key_cipher IS NOT NULL) AND r.route_key IN (?,'DEFAULT') ORDER BY (r.route_key=?) DESC,r.priority,r.id LIMIT 1",
            team,
            key,
            key);
    if (rows.isEmpty()) throw BusinessException.invalid("没有可用模型路由，请检查路由、模型、供应商状态及 API Key");
    return rows.getFirst();
  }

  public Map<String, Object> preview(String key) {
    String team = team();
    if (!List.of("INTENT_ANALYSIS", "TOOL_REASONING", "DEFAULT").contains(key))
      throw BusinessException.invalid("无效路由类型");
    var result = new LinkedHashMap<>(resolveManaged(team, key));
    result.remove("api_key_cipher");
    return result;
  }

  public void reconcile(String id, boolean release, String reason) {
    String team = team();
    if (reason == null || reason.isBlank() || reason.length() > 500)
      throw BusinessException.invalid("请填写对账依据（最多500字）");
    tx.executeWithoutResult(
        t -> {
          lock(team);
          var l =
              db.one(
                  "SELECT * FROM llm_token_ledger WHERE id=? AND team_id=? FOR UPDATE", id, team);
          if (!List.of("RESERVED", "UNCERTAIN").contains(l.get("status")))
            throw BusinessException.conflict("记录已结算");
          if (l.get("status").equals("RESERVED")
              && ((java.sql.Timestamp) l.get("created_at"))
                  .toInstant()
                  .isAfter(Instant.now().minusSeconds(120)))
            throw BusinessException.conflict("调用仍可能运行，请稍后对账");
          long amount = ((Number) l.get("reserved_tokens")).longValue();
          db.update(
              "UPDATE llm_monthly_usage SET reserved_tokens=reserved_tokens-?,used_tokens=used_tokens+? WHERE team_id=? AND month_key=?",
              amount,
              release ? 0 : amount,
              team,
              l.get("month_key"));
          db.update(
              "UPDATE llm_token_ledger SET status=?,charged_tokens=?,estimated=TRUE WHERE id=?",
              release ? "RELEASED" : "RECONCILED",
              release ? 0 : amount,
              id);
          audit.record(
              team,
              l.get("workflow_id").toString(),
              auth.current().id(),
              "TOKEN_RECONCILED",
              id + " " + (release ? "确认未计费，释放" : "按预留上界扣除") + "：" + reason);
        });
  }
}
