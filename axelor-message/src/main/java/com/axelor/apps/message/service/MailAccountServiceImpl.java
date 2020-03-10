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
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.message.exception.IExceptionMessage;
import com.axelor.apps.tool.date.DateTool;
import com.axelor.apps.tool.service.CipherService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.mail.ImapAccount;
import com.axelor.mail.MailConstants;
import com.axelor.mail.MailParser;
import com.axelor.mail.MailReader;
import com.axelor.mail.Pop3Account;
import com.axelor.mail.SmtpAccount;
import com.axelor.meta.MetaFiles;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.activation.DataSource;
import javax.mail.AuthenticationFailedException;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.search.FlagTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MailAccountServiceImpl implements MailAccountService {

  private final Logger log = LoggerFactory.getLogger(MailAccountServiceImpl.class);

  static final int CHECK_CONF_TIMEOUT = 5000;

  @Inject protected EmailAccountRepository mailAccountRepo;

  @Inject private CipherService cipherService;

  @Inject protected EmailAddressRepository emailAddressRepo;

  @Inject private MessageRepository messageRepo;

  @Inject private MetaFiles metaFiles;

  @Override
  public void checkDefaultMailAccount(EmailAccount mailAccount) throws AxelorException {

    if (mailAccount.getIsDefault()) {
      String query = "self.isDefault = true";
      List<Object> params = Lists.newArrayList();
      if (mailAccount.getId() != null) {
        query += " AND self.id != ?1";
        params.add(mailAccount.getId());
      }

      Integer serverTypeSelect = mailAccount.getServerTypeSelect();
      if (serverTypeSelect == EmailAccountRepository.SERVER_TYPE_SMTP) {
        query += " AND self.serverTypeSelect = " + EmailAccountRepository.SERVER_TYPE_SMTP + " ";
      } else if (serverTypeSelect == EmailAccountRepository.SERVER_TYPE_IMAP
          || serverTypeSelect == EmailAccountRepository.SERVER_TYPE_POP) {
        query +=
            " AND (self.serverTypeSelect = "
                + EmailAccountRepository.SERVER_TYPE_IMAP
                + " OR "
                + "self.serverTypeSelect = "
                + EmailAccountRepository.SERVER_TYPE_POP
                + ") ";
      }

      Long count = mailAccountRepo.all().filter(query, params.toArray()).count();
      if (count > 0) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.MAIL_ACCOUNT_5));
      }
    }
  }

  @Override
  public EmailAccount getDefaultSender() {

    return mailAccountRepo
        .all()
        .filter(
            "self.isDefault = true AND self.serverTypeSelect = ?1",
            EmailAccountRepository.SERVER_TYPE_SMTP)
        .fetchOne();
  }

  @Override
  public EmailAccount getDefaultReader() {

    return mailAccountRepo
        .all()
        .filter(
            "self.isDefault = true "
                + "AND (self.serverTypeSelect = ?1 OR self.serverTypeSelect = ?2)",
            EmailAccountRepository.SERVER_TYPE_IMAP,
            EmailAccountRepository.SERVER_TYPE_POP)
        .fetchOne();
  }

  @Override
  public void checkMailAccountConfiguration(EmailAccount mailAccount)
      throws AxelorException, MessagingException {

    com.axelor.mail.MailAccount account = getMailAccount(mailAccount);

    Session session = account.getSession();

    try {

      if (mailAccount.getServerTypeSelect().equals(EmailAccountRepository.SERVER_TYPE_SMTP)) {
        Transport transport = session.getTransport(getProtocol(mailAccount));
        transport.connect(
            mailAccount.getHost(),
            mailAccount.getPort(),
            mailAccount.getLogin(),
            mailAccount.getPassword());
        transport.close();
      } else {
        session.getStore().connect();
      }

    } catch (AuthenticationFailedException e) {
      throw new AxelorException(
          e,
          mailAccount,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.MAIL_ACCOUNT_1));
    } catch (NoSuchProviderException e) {
      throw new AxelorException(
          e,
          mailAccount,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.MAIL_ACCOUNT_2));
    }
  }

  @Override
  public com.axelor.mail.MailAccount getMailAccount(EmailAccount mailAccount) {

    Integer serverType = mailAccount.getServerTypeSelect();

    String port = mailAccount.getPort() <= 0 ? null : mailAccount.getPort().toString();

    com.axelor.mail.MailAccount account;

    if (serverType == EmailAccountRepository.SERVER_TYPE_SMTP) {
      account =
          new SmtpAccount(
              mailAccount.getHost(),
              port,
              mailAccount.getLogin(),
              getDecryptPassword(mailAccount.getPassword()),
              getSecurity(mailAccount));
    } else if (serverType == EmailAccountRepository.SERVER_TYPE_IMAP) {
      account =
          new ImapAccount(
              mailAccount.getHost(),
              mailAccount.getPort().toString(),
              mailAccount.getLogin(),
              getDecryptPassword(mailAccount.getPassword()),
              getSecurity(mailAccount));
    } else {
      account =
          new Pop3Account(
              mailAccount.getHost(),
              mailAccount.getPort().toString(),
              mailAccount.getLogin(),
              getDecryptPassword(mailAccount.getPassword()),
              getSecurity(mailAccount));
    }

    account.setConnectionTimeout(CHECK_CONF_TIMEOUT);

    return account;
  }

  public String getSecurity(EmailAccount mailAccount) {

    if (mailAccount.getSecuritySelect() == EmailAccountRepository.SECURITY_SSL) {
      return MailConstants.CHANNEL_SSL;
    } else if (mailAccount.getSecuritySelect() == EmailAccountRepository.SECURITY_STARTTLS) {
      return MailConstants.CHANNEL_STARTTLS;
    } else {
      return null;
    }
  }

  public String getProtocol(EmailAccount mailAccount) {

    switch (mailAccount.getServerTypeSelect()) {
      case EmailAccountRepository.SERVER_TYPE_SMTP:
        return "smtp";
      case EmailAccountRepository.SERVER_TYPE_IMAP:
        if (mailAccount.getSecuritySelect() == EmailAccountRepository.SECURITY_SSL) {
          return MailConstants.PROTOCOL_IMAPS;
        }
        return MailConstants.PROTOCOL_IMAP;
      case EmailAccountRepository.SERVER_TYPE_POP:
        return MailConstants.PROTOCOL_POP3;
      default:
        return "";
    }
  }

  public String getSignature(EmailAccount mailAccount) {

    if (mailAccount != null && mailAccount.getSignature() != null) {
      return "\n " + mailAccount.getSignature();
    }
    return "";
  }

  @Override
  public int fetchEmails(EmailAccount mailAccount, boolean unseenOnly)
      throws MessagingException, IOException {

    if (mailAccount == null) {
      return 0;
    }

    log.debug(
        "Fetching emails from host: {}, port: {}, login: {} ",
        mailAccount.getHost(),
        mailAccount.getPort(),
        mailAccount.getLogin());

    com.axelor.mail.MailAccount account = null;
    if (mailAccount.getServerTypeSelect().equals(EmailAccountRepository.SERVER_TYPE_IMAP)) {
      account =
          new ImapAccount(
              mailAccount.getHost(),
              mailAccount.getPort().toString(),
              mailAccount.getLogin(),
              mailAccount.getPassword(),
              getSecurity(mailAccount));
    } else {
      account =
          new Pop3Account(
              mailAccount.getHost(),
              mailAccount.getPort().toString(),
              mailAccount.getLogin(),
              mailAccount.getPassword(),
              getSecurity(mailAccount));
    }

    MailReader reader = new MailReader(account);
    final Store store = reader.getStore();
    final Folder inbox = store.getFolder("INBOX");

    // open as READ_WRITE to mark messages as seen
    inbox.open(Folder.READ_WRITE);

    // find all unseen messages
    final FetchProfile profile = new FetchProfile();
    javax.mail.Message[] messages;
    if (unseenOnly) {
      final FlagTerm unseen = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
      messages = inbox.search(unseen);
    } else {
      messages = inbox.getMessages();
    }

    profile.add(FetchProfile.Item.ENVELOPE);

    // actually fetch the messages
    inbox.fetch(messages, profile);
    log.debug("Total emails unseen: {}", messages.length);

    int count = 0;
    for (javax.mail.Message message : messages) {
      if (message instanceof MimeMessage) {
        MailParser parser = new MailParser((MimeMessage) message);
        parser.parse();
        createMessage(mailAccount, parser, message.getSentDate());
        count++;
      }
    }

    log.debug("Total emails fetched: {}", count);

    return count;
  }

  @Transactional(rollbackOn = {Exception.class})
  public Message createMessage(EmailAccount mailAccount, MailParser parser, Date date)
      throws MessagingException {

    Message message = new Message();

    message.setMailAccount(mailAccount);
    message.setTypeSelect(MessageRepository.TYPE_SENT);
    message.setMediaTypeSelect(MessageRepository.MEDIA_TYPE_EMAIL);

    message.setFromEmailAddress(getEmailAddress(parser.getFrom()));
    message.setCcEmailAddressSet(getEmailAddressSet(parser.getCc()));
    message.setBccEmailAddressSet(getEmailAddressSet(parser.getBcc()));
    message.setToEmailAddressSet(getEmailAddressSet(parser.getTo()));
    message.addReplyToEmailAddressSetItem(getEmailAddress(parser.getReplyTo()));

    message.setContent(parser.getHtml());
    message.setSubject(parser.getSubject());
    message.setSentDateT(DateTool.toLocalDateT(date));

    message = messageRepo.save(message);

    List<DataSource> attachments = parser.getAttachments();
    addAttachments(message, attachments);

    return message;
  }

  protected EmailAddress getEmailAddress(InternetAddress address) {

    EmailAddress emailAddress = null;
    emailAddress = emailAddressRepo.findByAddress(address.getAddress());
    if (emailAddress == null) {
      emailAddress = new EmailAddress();
      emailAddress.setAddress(address.getAddress());
    }

    return emailAddress;
  }

  protected Set<EmailAddress> getEmailAddressSet(List<InternetAddress> addresses) {

    Set<EmailAddress> addressSet = new HashSet<>();

    if (addresses == null) {
      return addressSet;
    }

    for (InternetAddress address : addresses) {

      EmailAddress emailAddress = getEmailAddress(address);

      addressSet.add(emailAddress);
    }

    return addressSet;
  }

  protected void addAttachments(Message message, List<DataSource> attachments) {

    if (attachments == null) {
      return;
    }

    for (DataSource source : attachments) {
      try {
        InputStream stream = source.getInputStream();
        metaFiles.attach(stream, source.getName(), message);
      } catch (IOException e) {
        TraceBackService.trace(e);
      }
    }
  }

  @Override
  public String getEncryptPassword(String password) {

    return cipherService.encrypt(password);
  }

  @Override
  public String getDecryptPassword(String password) {

    return cipherService.decrypt(password);
  }
}
