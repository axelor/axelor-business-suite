package com.axelor.apps.message.service;

import com.axelor.auth.db.User;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.mail.MailConstants;
import com.axelor.mail.db.MailFlags;
import com.axelor.mail.db.MailFollower;
import com.axelor.mail.db.MailMessage;
import com.axelor.mail.db.repo.MailFollowerRepository;
import com.axelor.mail.db.repo.MailMessageRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class MailMessageServiceImpl implements MailMessageService {

  protected MailMessageRepository mailMessageRepository;
  protected MailFollowerRepository mailFollowerRepository;

  @Inject
  public MailMessageServiceImpl(
      MailMessageRepository mailMessageRepository, MailFollowerRepository mailFollowerRepository) {
    this.mailMessageRepository = mailMessageRepository;
    this.mailFollowerRepository = mailFollowerRepository;
  }

  @Override
  @Transactional
  public void sendNotification(User user, String subject, String body) {
    this.sendNotification(user, subject, body, null, null);
  }

  @Override
  @Transactional
  public void sendNotification(
      User user, String subject, String body, Long relatedId, Class<? extends Model> relatedModel) {
    MailMessage message = new MailMessage();

    message.setSubject(subject);
    message.setBody(body);

    message.setAuthor(user);
    message.setType(MailConstants.MESSAGE_TYPE_COMMENT);

    if (relatedId != null && relatedModel != null) {
      message.setRelatedId(relatedId);
      message.setRelatedModel(relatedModel.getName());
    }

    MailFlags flags = new MailFlags();
    flags.setMessage(message);
    flags.setUser(user);
    flags.setIsRead(Boolean.FALSE);
    message.addFlag(flags);

    mailMessageRepository.save(message);

    if (relatedId == null || relatedModel == null) {
      return;
    }

    MailFollower follower = mailFollowerRepository.findOne(JPA.find(relatedModel, relatedId), user);
    if (follower != null && Boolean.FALSE.equals(follower.getArchived())) {
      return;
    }

    if (follower == null) {
      follower = new MailFollower();
    }

    follower.setArchived(false);
    follower.setRelatedId(relatedId);
    follower.setRelatedModel(relatedModel.getName());
    follower.setUser(user);

    mailFollowerRepository.save(follower);
  }
}
