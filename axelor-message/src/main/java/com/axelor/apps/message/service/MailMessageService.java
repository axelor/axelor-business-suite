package com.axelor.apps.message.service;

import com.axelor.auth.db.User;
import com.axelor.db.Model;

public interface MailMessageService {

  /**
   * Send notification to user via AOP MailMessage from a process.
   *
   * @param user the user which is going to have the notification
   * @param subject the subject of the message
   * @param body the body of the message
   */
  void sendNotification(User user, String subject, String body);

  /**
   * Send notification to user via AOP MailMessage from a process.
   *
   * @param user the user which is going to have the notification
   * @param subject the subject of the message
   * @param body the body of the message
   * @param relatedId related id of the model used in the process
   * @param relatedModel related model used in the process
   */
  void sendNotification(
      User user, String subject, String body, Long relatedId, Class<? extends Model> relatedModel);
}
