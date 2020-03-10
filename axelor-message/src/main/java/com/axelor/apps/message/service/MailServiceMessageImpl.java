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

import static com.axelor.common.StringUtils.isBlank;

import com.axelor.apps.message.db.EmailAccount;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.auth.AuditableRunner;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.inject.Beans;
import com.axelor.mail.MailAccount;
import com.axelor.mail.MailBuilder;
import com.axelor.mail.MailException;
import com.axelor.mail.MailReader;
import com.axelor.mail.MailSender;
import com.axelor.mail.db.MailMessage;
import com.axelor.mail.db.repo.MailMessageRepository;
import com.axelor.mail.service.MailService;
import com.axelor.mail.service.MailServiceImpl;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaAttachment;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Singleton;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MailServiceMessageImpl extends MailServiceImpl {

  private Logger log = LoggerFactory.getLogger(MailService.class);

  private ExecutorService executor = Executors.newCachedThreadPool();

  private MailSender sender = null;

  private MailReader reader = null;

  private EmailAccount senderAccount = null;

  private EmailAccount readerAccount = null;

  @Inject private MailAccountService mailAccountService;

  @Override
  public Model resolve(String email) {
    final EmailAddressRepository addresses = Beans.get(EmailAddressRepository.class);
    final EmailAddress address = addresses.all().filter("self.address = ?1", email).fetchOne();
    if (address != null) {
      return address;
    }
    return super.resolve(email);
  }

  @Override
  public List<InternetAddress> findEmails(String matching, List<String> selected, int maxResult) {

    final List<String> where = new ArrayList<>();
    final Map<String, Object> params = new HashMap<>();

    where.add("self.address is not null");

    if (!isBlank(matching)) {
      where.add("(LOWER(self.address) like LOWER(:email))");
      params.put("email", "%" + matching + "%");
    }
    if (selected != null && !selected.isEmpty()) {
      where.add("self.address not in (:selected)");
      params.put("selected", selected);
    }

    final String filter = Joiner.on(" AND ").join(where);
    final Query<EmailAddress> query = Query.of(EmailAddress.class);

    if (!isBlank(filter)) {
      query.filter(filter);
      query.bind(params);
    }

    final List<InternetAddress> addresses = new ArrayList<>();
    for (EmailAddress emailAddress : query.fetch(maxResult)) {
      try {
        final InternetAddress item = new InternetAddress(emailAddress.getAddress());
        addresses.add(item);
      } catch (AddressException e) {
      }
    }

    return addresses;
  }

  @Override
  public void fetch() throws MailException {

    final EmailAccount emailAccount = mailAccountService.getDefaultReader();
    if (emailAccount == null) {
      super.fetch();
    } else {
      final MailReader reader = getMailReader(emailAccount);
      final AuditableRunner runner = Beans.get(AuditableRunner.class);
      runner.run(
          () -> {
            try {
              fetch(reader);
            } catch (Exception e) {
              log.error("Unable to fetch messages", e);
            }
          });
    }
  }

  @Override
  public void send(final MailMessage message) throws MailException {
    final EmailAccount emailAccount = mailAccountService.getDefaultSender();
    if (emailAccount == null) {
      super.send(message);
      return;
    }

    Preconditions.checkNotNull(message, "mail message can't be null");

    final Model related = findEntity(message);
    final MailSender sender = getMailSender(emailAccount);

    final Set<String> recipients = recipients(message, related);
    if (recipients.isEmpty()) {
      return;
    }

    final MailMessageRepository messages = Beans.get(MailMessageRepository.class);
    final MailBuilder builder = sender.compose().subject(getSubject(message, related));

    for (String recipient : recipients) {
      builder.to(recipient);
    }

    for (MetaAttachment attachment : messages.findAttachments(message)) {
      final Path filePath = MetaFiles.getPath(attachment.getMetaFile());
      final File file = filePath.toFile();
      builder.attach(file.getName(), file.toString());
    }

    final MimeMessage email;
    try {
      builder.html(template(message, related));
      email = builder.build(message.getMessageId());
      final Set<String> references = new LinkedHashSet<>();
      if (message.getParent() != null) {
        references.add(message.getParent().getMessageId());
      }
      if (message.getRoot() != null) {
        references.add(message.getRoot().getMessageId());
      }
      if (!references.isEmpty()) {
        email.setHeader("References", Joiner.on(" ").skipNulls().join(references));
      }
    } catch (MessagingException | IOException e) {
      throw new MailException(e);
    }

    // send email using a separate process to void thread blocking
    executor.submit(
        new Callable<Boolean>() {
          @Override
          public Boolean call() throws Exception {
            send(sender, email);
            return true;
          }
        });
  }

  protected MailSender getMailSender(EmailAccount emailAccount) {

    if (senderAccount == null
        || !senderAccount.getId().equals(emailAccount.getId())
        || !senderAccount.getVersion().equals(emailAccount.getVersion())) {
      senderAccount = emailAccount;
      sender = null;
    }

    if (sender == null) {
      MailAccount mailAccount = mailAccountService.getMailAccount(emailAccount);
      sender = new MailSender(mailAccount);
    }

    return sender;
  }

  protected MailReader getMailReader(EmailAccount emailAccount) {

    if (readerAccount == null
        || !readerAccount.getId().equals(emailAccount.getId())
        || !readerAccount.getVersion().equals(emailAccount.getVersion())) {
      readerAccount = emailAccount;
      reader = null;
    }

    if (reader == null) {
      MailAccount mailAccount = mailAccountService.getMailAccount(emailAccount);
      reader = new MailReader(mailAccount);
    }

    return reader;
  }
}
