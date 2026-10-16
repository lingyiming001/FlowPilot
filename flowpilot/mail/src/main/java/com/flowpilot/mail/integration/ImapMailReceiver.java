package com.flowpilot.mail.integration;

import com.flowpilot.integration.service.MailIdentityVerifier;
import com.flowpilot.mail.service.MailService;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.FlagTerm;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "flowpilot.imap-enabled", havingValue = "true")
public class ImapMailReceiver {
  private final MailService mails;
  private final MailIdentityVerifier verifier;
  private final String host, user, password, account;
  private final int port;

  public ImapMailReceiver(
      MailService mails,
      MailIdentityVerifier verifier,
      @Value("${flowpilot.imap-host}") String host,
      @Value("${flowpilot.imap-port:993}") int port,
      @Value("${spring.mail.username}") String user,
      @Value("${spring.mail.password}") String password,
      @Value("${flowpilot.imap-account-id}") String account) {
    this.mails = mails;
    this.verifier = verifier;
    this.host = host;
    this.port = port;
    this.user = user;
    this.password = password;
    this.account = account;
  }

  @Scheduled(fixedDelay = 30000)
  public void poll() {
    Properties p = new Properties();
    p.setProperty("mail.imaps.connectiontimeout", "5000");
    p.setProperty("mail.imaps.timeout", "10000");
    p.setProperty("mail.imaps.writetimeout", "10000");
    try (Store store = Session.getInstance(p).getStore("imaps")) {
      store.connect(host, port, user, password);
      try (Folder inbox = store.getFolder("INBOX")) {
        inbox.open(Folder.READ_WRITE);
        var pending = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
        for (int i = 0; i < Math.min(pending.length, 20); i++) {
          var msg = pending[i];
          if (msg.getSize() > 1024 * 1024) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                .warn("Inbound message exceeds configured size limit");
            continue;
          }
          String[] ids = msg.getHeader("Message-ID");
          String messageId = ids == null ? null : ids[0];
          Address[] from = msg.getFrom();
          if (from == null || from.length != 1 || !(from[0] instanceof InternetAddress address))
            continue;
          String sender = address.getAddress();
          String text = plain(msg, 0);
          if (text.length() > 64000) text = text.substring(0, 64000);
          boolean verified = messageId != null && verifier.verify(account, messageId, sender);
          mails.receive(
              account,
              messageId,
              sender,
              Objects.requireNonNullElse(msg.getSubject(), "无主题"),
              text,
              verified);
          msg.setFlag(Flags.Flag.SEEN, true);
        }
      }
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .warn("IMAP receive failed type={}", e.getClass().getSimpleName());
    }
  }

  private String plain(Part part, int depth) throws Exception {
    if (depth > 8) return "";
    if (part.isMimeType("text/plain")) return String.valueOf(part.getContent());
    if (part.isMimeType("multipart/*")) {
      Multipart parts = (Multipart) part.getContent();
      StringBuilder body = new StringBuilder();
      for (int i = 0; i < Math.min(parts.getCount(), 20) && body.length() < 64000; i++) {
        BodyPart child = parts.getBodyPart(i);
        if (!Part.ATTACHMENT.equalsIgnoreCase(child.getDisposition()))
          body.append(plain(child, depth + 1)).append("\n");
      }
      return body.toString();
    }
    return "[非纯文本邮件，请人工处理]";
  }
}
