/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2014 Axelor (<http://axelor.com>).
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
package com.axelor.apps.base.service.message;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.ReportSettings;
import com.axelor.apps.base.db.BirtTemplate;
import com.axelor.apps.base.db.BirtTemplateParameter;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.PrintingSettings;
import com.axelor.apps.base.service.administration.GeneralService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.message.service.MailAccountService;
import com.axelor.apps.message.service.MessageServiceImpl;
import com.axelor.auth.AuthUtils;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.repo.MetaAttachmentRepository;
import com.axelor.tool.template.TemplateMaker;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class MessageServiceBaseImpl extends MessageServiceImpl {

	private final Logger LOG = LoggerFactory.getLogger( getClass() );

	@Inject
	private UserService userService;

	@Inject
	protected GeneralService generalService;

	@Inject
	public MessageServiceBaseImpl( MetaAttachmentRepository metaAttachmentRepository, MailAccountService mailAccountService, UserService userService ) {
		super(metaAttachmentRepository, mailAccountService);
		this.userService = userService;
	}


	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Message createMessage(String model, int id, String subject, String content, EmailAddress fromEmailAddress, List<EmailAddress> replyToEmailAddressList, List<EmailAddress> toEmailAddressList, List<EmailAddress> ccEmailAddressList,
			List<EmailAddress> bccEmailAddressList, Set<MetaFile> metaFiles, String addressBlock, int mediaTypeSelect)  {

		Message message = super.createMessage( model, id, subject, content, fromEmailAddress, replyToEmailAddressList, toEmailAddressList, ccEmailAddressList, bccEmailAddressList, metaFiles, addressBlock, mediaTypeSelect) ;

		message.setCompany(userService.getUserActiveCompany());

		return save(message);

	}

	@Override
	public String printMessage(Message message){

		Company company = message.getCompany();
		if(company == null){ return null; }

		PrintingSettings printSettings = company.getPrintingSettings();
		if ( printSettings == null || printSettings.getDefaultMailBirtTemplate() == null ) { return null; }

		BirtTemplate birtTemplate = printSettings.getDefaultMailBirtTemplate();

		LOG.debug("Default BirtTemplate : {}",birtTemplate);

		TemplateMaker maker = new TemplateMaker( new Locale( AuthUtils.getUser().getLanguage() ), '$', '$');
		maker.setContext( JPA.find( message.getClass(), message.getId() ), "Message" );

		ReportSettings reportSettings = new ReportSettings(birtTemplate.getTemplateLink(), birtTemplate.getFormat());

		for ( BirtTemplateParameter birtTemplateParameter : birtTemplate.getBirtTemplateParameterList() )  {
			maker.setTemplate(birtTemplateParameter.getValue());
			reportSettings.addParam(birtTemplateParameter.getName(), maker.make());
		}

		return reportSettings.getUrl();

	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Message sendMessage(Message message)  {

		super.sendMessage(message);

		if( !message.getStatusSelect().equals( MessageRepository.STATUS_SENT ) ){ return message; }

		message.setSentDateT( generalService.getTodayDateTime().toLocalDateTime() );
		return save(message);
	}

}