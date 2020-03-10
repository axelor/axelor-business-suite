/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.message.service;

import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.EmailAccountRepository;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.message.exception.IExceptionMessage;
import com.axelor.auth.AuthUtils;
import com.axelor.db.JPA;
import com.axelor.db.JpaSupport;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.mail.MailBuilder;
import com.axelor.mail.MailSender;
import com.axelor.mail.SmtpAccount;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaAttachment;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.repo.MetaAttachmentRepository;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.mail.MessagingException;
import javax.persistence.EntityNotFoundException;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageServiceImpl extends JpaSupport implements MessageService {
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private MetaAttachmentRepository metaAttachmentRepository;
  protected MessageRepository messageRepository;

  private ExecutorService executor = Executors.newCachedThreadPool();
  private static final int ENTITY_FIND_TIMEOUT = 10000;
  private static final int ENTITY_FIND_INTERVAL = 200;

  @Inject
  public MessageServiceImpl(
      MetaAttachmentRepository metaAttachmentRepository, MessageRepository messageRepository) {
    this.metaAttachmentRepository = metaAttachmentRepository;
    this.messageRepository = messageRepository;
  }

  @Override
  @Transactional
  public Message createMessage(
      String model,
      int id,
      String subject,
      String content,
      EmailAddress fromEmailAddress,
      List<EmailAddress> replyToEmailAddressList,
      List<EmailAddress> toEmailAddressList,
      List<EmailAddress> ccEmailAddressList,
      List<EmailAddress> bccEmailAddressList,
      Set<MetaFile> metaFiles,
      String addressBlock,
      int mediaTypeSelect,
      EmailAccount emailAccount) {

    emailAccount =
        emailAccount != null
            ? Beans.get(EmailAccountRepository.class).find(emailAccount.getId())
            : emailAccount;
    Message message =
        createMessage(
            content,
            fromEmailAddress,
            model,
            id,
            null,
            0,
            false,
            MessageRepository.STATUS_DRAFT,
            subject,
            MessageRepository.TYPE_SENT,
            replyToEmailAddressList,
            toEmailAddressList,
            ccEmailAddressList,
            bccEmailAddressList,
            addressBlock,
            mediaTypeSelect,
            emailAccount);

    messageRepository.save(message);

    attachMetaFiles(message, metaFiles);

    return message;
  }

  @Override
  @Transactional
  public void attachMetaFiles(Message message, Set<MetaFile> metaFiles) {

    Preconditions.checkNotNull(message.getId());

    if (metaFiles == null || metaFiles.isEmpty()) {
      return;
    }

    log.debug("Add metafiles to object {}:{}", Message.class.getName(), message.getId());

    for (MetaFile metaFile : metaFiles) {
      Beans.get(MetaFiles.class).attach(metaFile, metaFile.getFileName(), message);
    }
  }

  protected Message createMessage(
      String content,
      EmailAddress fromEmailAddress,
      String relatedTo1Select,
      long relatedTo1SelectId,
      String relatedTo2Select,
      long relatedTo2SelectId,
      boolean sentByEmail,
      int statusSelect,
      String subject,
      int typeSelect,
      List<EmailAddress> replyToEmailAddressList,
      List<EmailAddress> toEmailAddressList,
      List<EmailAddress> ccEmailAddressList,
      List<EmailAddress> bccEmailAddressList,
      String addressBlock,
      int mediaTypeSelect,
      EmailAccount emailAccount) {

    Set<EmailAddress> replyToEmailAddressSet = Sets.newHashSet();
    Set<EmailAddress> bccEmailAddressSet = Sets.newHashSet();
    Set<EmailAddress> toEmailAddressSet = Sets.newHashSet();
    Set<EmailAddress> ccEmailAddressSet = Sets.newHashSet();

    if (mediaTypeSelect == MessageRepository.MEDIA_TYPE_EMAIL) {
      if (replyToEmailAddressList != null) {
        replyToEmailAddressSet.addAll(replyToEmailAddressList);
      }
      if (bccEmailAddressList != null) {
        bccEmailAddressSet.addAll(bccEmailAddressList);
      }
      if (toEmailAddressList != null) {
        toEmailAddressSet.addAll(toEmailAddressList);
      }
      if (ccEmailAddressList != null) {
        ccEmailAddressSet.addAll(ccEmailAddressList);
      }
    }

    if (emailAccount != null) {
      content += "<p></p><p></p>" + Beans.get(MailAccountService.class).getSignature(emailAccount);
    }

    Message message =
        new Message(
            typeSelect,
            subject,
            content,
            statusSelect,
            mediaTypeSelect,
            addressBlock,
            fromEmailAddress,
            replyToEmailAddressSet,
            toEmailAddressSet,
            ccEmailAddressSet,
            bccEmailAddressSet,
            sentByEmail,
            emailAccount);

    message.setRelatedTo1Select(relatedTo1Select);
    message.setRelatedTo1SelectId(relatedTo1SelectId);
    message.setRelatedTo2Select(relatedTo2Select);
    message.setRelatedTo2SelectId(relatedTo2SelectId);

    return message;
  }

  public Message sendMessage(Message message) throws AxelorException {
    try {
      if (message.getMediaTypeSelect() == MessageRepository.MEDIA_TYPE_MAIL) {
        return sendByMail(message);
      } else if (message.getMediaTypeSelect() == MessageRepository.MEDIA_TYPE_EMAIL) {
        return sendByEmail(message);
      } else if (message.getMediaTypeSelect() == MessageRepository.MEDIA_TYPE_CHAT) {
        return sendToUser(message);
      }
    } catch (MessagingException e) {
      TraceBackService.trace(e);
    }
    return message;
  }

  @Transactional
  public Message sendToUser(Message message) {

    if (message.getRecipientUser() == null) {
      return message;
    }

    message.setSenderUser(AuthUtils.getUser());
    log.debug("Sent internal message to user ::: {}", message.getRecipientUser());

    message.setStatusSelect(MessageRepository.STATUS_SENT);
    message.setSentByEmail(false);
    message.setSentDateT(LocalDateTime.now());
    return messageRepository.save(message);
  }

  @Transactional
  public Message sendByMail(Message message) {

    log.debug("Sent mail");
    message.setStatusSelect(MessageRepository.STATUS_SENT);
    message.setSentByEmail(false);
    message.setSentDateT(LocalDateTime.now());
    return messageRepository.save(message);
  }

  @Transactional(rollbackOn = {Exception.class})
  public Message sendByEmail(Message message) throws MessagingException, AxelorException {

    EmailAccount mailAccount = message.getMailAccount();

    if (mailAccount == null) {
      return message;
    }

    log.debug("Sent email");
    MailAccountService mailAccountService = Beans.get(MailAccountService.class);
    com.axelor.mail.MailAccount account =
        new SmtpAccount(
            mailAccount.getHost(),
            mailAccount.getPort().toString(),
            mailAccount.getLogin(),
            mailAccountService.getDecryptPassword(mailAccount.getPassword()),
            mailAccountService.getSecurity(mailAccount));

    List<String> replytoRecipients = this.getEmailAddresses(message.getReplyToEmailAddressSet());
    List<String> toRecipients = this.getEmailAddresses(message.getToEmailAddressSet());
    List<String> ccRecipients = this.getEmailAddresses(message.getCcEmailAddressSet());
    List<String> bccRecipients = this.getEmailAddresses(message.getBccEmailAddressSet());

    if (toRecipients.isEmpty() && ccRecipients.isEmpty() && bccRecipients.isEmpty()) {
      throw new AxelorException(
          message,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.MESSAGE_6));
    }

    MailSender sender = new MailSender(account);
    MailBuilder mailBuilder = sender.compose();

    mailBuilder.subject(message.getSubject());

    if (message.getFromEmailAddress() != null) {
      if (!Strings.isNullOrEmpty(message.getFromEmailAddress().getAddress())) {
        log.debug("Override from :::  {}", this.getFullEmailAddress(message.getFromEmailAddress()));
        mailBuilder.from(this.getFullEmailAddress(message.getFromEmailAddress()));
      } else {
        throw new AxelorException(
            message, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR, IExceptionMessage.MESSAGE_5);
      }
    }
    if (replytoRecipients != null && !replytoRecipients.isEmpty()) {
      mailBuilder.replyTo(Joiner.on(",").join(replytoRecipients));
    }
    if (toRecipients != null && !toRecipients.isEmpty()) {
      mailBuilder.to(Joiner.on(",").join(toRecipients));
    }
    if (ccRecipients != null && !ccRecipients.isEmpty()) {
      mailBuilder.cc(Joiner.on(",").join(ccRecipients));
    }
    if (bccRecipients != null && !bccRecipients.isEmpty()) {
      mailBuilder.bcc(Joiner.on(",").join(bccRecipients));
    }
    if (!Strings.isNullOrEmpty(message.getContent())) {
      mailBuilder.html(message.getContent());
    }

    for (MetaAttachment metaAttachment : getMetaAttachments(message)) {
      MetaFile metaFile = metaAttachment.getMetaFile();
      mailBuilder.attach(metaFile.getFileName(), MetaFiles.getPath(metaFile).toString());
    }

    getEntityManager().flush();
    getEntityManager().lock(message, LockModeType.PESSIMISTIC_WRITE);
    final long messageId = message.getId();
    // send email using a separate process to avoid thread blocking
    executor.submit(
        () -> {
          final long startTime = System.currentTimeMillis();
          boolean done = false;
          PersistenceException persistenceException = null;
          mailBuilder.send();
          do {
            try {
              inTransaction(
                  () -> {
                    final Message updateMessage = findMessage(messageId);
                    getEntityManager().lock(updateMessage, LockModeType.PESSIMISTIC_WRITE);
                    updateMessage.setSentByEmail(true);
                    updateMessage.setStatusSelect(MessageRepository.STATUS_SENT);
                    updateMessage.setSentDateT(LocalDateTime.now());
                    updateMessage.setSenderUser(AuthUtils.getUser());
                    messageRepository.save(updateMessage);
                  });
              done = true;
            } catch (PersistenceException e) {
              persistenceException = e;
              sleep();
            }
          } while (!done && System.currentTimeMillis() - startTime < ENTITY_FIND_TIMEOUT);
          if (!done) {
            throw persistenceException;
          }
          return true;
        });

    return message;
  }

  protected Message findMessage(Long messageId) {
    Message foundMessage;
    final long startTime = System.currentTimeMillis();

    while ((foundMessage = messageRepository.find(messageId)) == null
        && System.currentTimeMillis() - startTime < ENTITY_FIND_TIMEOUT) {
      sleep();
    }

    if (foundMessage == null) {
      throw new EntityNotFoundException(messageId.toString());
    }

    return foundMessage;
  }

  protected void sleep() {
    try {
      Thread.sleep(ENTITY_FIND_INTERVAL);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public Set<MetaAttachment> getMetaAttachments(Message message) {

    Query<MetaAttachment> query =
        metaAttachmentRepository
            .all()
            .filter(
                "self.objectId = ?1 AND self.objectName = ?2",
                message.getId(),
                Message.class.getName());
    return Sets.newHashSet(query.fetch());
  }

  public List<String> getEmailAddresses(Set<EmailAddress> emailAddressSet) {

    List<String> recipients = Lists.newArrayList();
    if (emailAddressSet != null) {
      for (EmailAddress emailAddress : emailAddressSet) {

        if (Strings.isNullOrEmpty(emailAddress.getAddress())) {
          continue;
        }
        recipients.add(this.getFullEmailAddress(emailAddress));
      }
    }

    return recipients;
  }

  @Override
  public String printMessage(Message message) throws AxelorException {
    return null;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Message regenerateMessage(Message message) throws Exception {
    Preconditions.checkNotNull(
        message.getTemplate(),
        I18n.get("Cannot regenerate message without template associated to message."));
    Preconditions.checkNotNull(
        message.getRelatedTo1Select(),
        I18n.get("Cannot regenerate message without related model."));
    Class m = Class.forName(message.getRelatedTo1Select());
    Model model = JPA.all(m).filter("self.id = ?", message.getRelatedTo1SelectId()).fetchOne();
    Message newMessage =
        Beans.get(TemplateMessageService.class).generateMessage(model, message.getTemplate());
    newMessage.setRelatedTo2Select(message.getRelatedTo2Select());
    newMessage.setRelatedTo2SelectId(message.getRelatedTo2SelectId());
    message.setArchived(true);
    return newMessage;
  }

  @Override
  public String getFullEmailAddress(EmailAddress emailAddress) {
    return emailAddress.getAddress();
  }
}
