package com.axelor.apps.hr.service;

import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.Template;
import com.axelor.apps.message.service.MessageService;
import com.axelor.apps.message.service.TemplateMessageService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;

public class MailManagementService {
	
	@Inject
	protected MessageService messageService;

	@Inject
	protected TemplateMessageService templateMessageService;
	
	
	public boolean sendEmail(Template template,long objectId) throws AxelorException{
		if(template!=null){
			sendEmailTemplate(template,objectId);
			return true;
		}
		return false;
	}

	public void sendEmailTemplate(Template template,long objectId){
		String model = template.getMetaModel().getFullName();
		String tag = template.getMetaModel().getName();
		Message message = new Message();
		try{
			message = templateMessageService.generateMessage(objectId, model, tag, template);
			message = messageService.sendByEmail(message);
		}
		catch(Exception e){
			TraceBackService.trace(new Exception(e));
		}
	}
}
