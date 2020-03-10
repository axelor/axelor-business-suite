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
package com.axelor.apps.talent.service;

import com.axelor.apps.base.db.AppRecruitment;
import com.axelor.apps.base.db.repo.AppRecruitmentRepository;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.talent.db.JobApplication;
import com.axelor.apps.talent.db.JobPosition;
import com.axelor.apps.talent.db.repo.JobApplicationRepository;
import com.axelor.apps.talent.db.repo.JobPositionRepository;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaAttachment;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.repo.MetaAttachmentRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.util.List;

public class JobPositionServiceImpl implements JobPositionService {

  @Inject private JobPositionRepository jobPositionRepo;

  @Inject private JobApplicationRepository jobApplicationRepo;

  @Inject private AppRecruitmentRepository appRecruitmentRepo;

  @Inject private MetaFiles metaFiles;

  @Inject private MetaAttachmentRepository metaAttachmentRepo;

  @Override
  public void createJobApplication(Message message) {

    List<JobPosition> jobPositions =
        jobPositionRepo.all().filter("self.mailAccount = ?1", message.getMailAccount()).fetch();

    if (jobPositions.isEmpty()) {
      return;
    }

    if (jobPositions.size() > 1) {
      boolean positionFound = false;
      for (JobPosition position : jobPositions) {
        if (position.getJobReference() != null
            && message.getSubject().contains(position.getJobReference())) {
          createApplication(position, message);
          positionFound = true;
        }
      }
      if (!positionFound) {
        createApplication(null, message);
      }
    } else {
      createApplication(jobPositions.get(0), message);
    }

    updateLastEmailId(message);
  }

  protected void createApplication(JobPosition position, Message message) {

    JobApplication application = new JobApplication();

    if (position != null) {
      application.setJobPosition(position);
      application.setResponsible(position.getEmployee());
    }

    application.setEmailAddress(message.getFromEmailAddress());
    application.setDescription(message.getContent());

    application = saveApplication(application);

    try {
      copyAttachments(application, message);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Transactional
  public JobApplication saveApplication(JobApplication application) {
    if (application != null) {
      application = jobApplicationRepo.save(application);
    }

    return application;
  }

  @Transactional
  public void updateLastEmailId(Message message) {

    AppRecruitment appRecruitment = appRecruitmentRepo.all().fetchOne();
    appRecruitment.setLastEmailId(message.getId().toString());

    appRecruitmentRepo.save(appRecruitment);
  }

  protected void copyAttachments(JobApplication application, Message message) throws IOException {

    List<MetaAttachment> attachments =
        metaAttachmentRepo
            .all()
            .filter(
                "self.objectId = ?1 and self.objectName = ?2",
                message.getId(),
                Message.class.getName())
            .fetch();

    for (MetaAttachment attachment : attachments) {
      MetaFile file = attachment.getMetaFile();
      metaFiles.attach(file, file.getFileName(), application);
    }
  }
}
