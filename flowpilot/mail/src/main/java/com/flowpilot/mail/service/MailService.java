package com.flowpilot.mail.service;

import java.util.*;

public interface MailService {
  List<Map<String, Object>> list(String team);

  Map<String, Object> detail(String id, String team);

  String receive(
      String account, String messageId, String from, String subject, String body, boolean trusted);
}
