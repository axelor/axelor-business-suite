/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.base.service.batch;

import com.axelor.apps.base.db.PasswordChangeBatch;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.auth.AuthService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.shiro.session.Session;

public class PasswordChangeBatchService extends AbstractBatch {

  @Inject protected UserRepository userRepo;
  @Inject protected UserService userService;
  @Inject protected AuthService authService;

  @Override
  protected void process() {

    PasswordChangeBatch passwordChangeBatch = batch.getPasswordChangeBatch();

    Set<User> userSet = getUsers(passwordChangeBatch);

    if (passwordChangeBatch.getGenerateNewRandomPasswords()
        && passwordChangeBatch.getUpdatePasswordNextLogin()) {
      updateUsers(userSet);
    } else {
      if (passwordChangeBatch.getGenerateNewRandomPasswords()) {
        generateRandomPasswordForUsers(userSet);
      }

      if (passwordChangeBatch.getUpdatePasswordNextLogin()) {
        updatePasswordNextLoginForUsers(userSet);
      }
    }
  }

  public Set<User> getUsers(PasswordChangeBatch passwordChangeBatch) {

    List<User> userList = new ArrayList<User>();
    LocalDate date = LocalDate.now().minusDays(passwordChangeBatch.getNbOfDaySinceLastUpdate());

    if (passwordChangeBatch.getAllUsers()) {
      userList =
          userRepo
              .all()
              .filter(
                  "cast(self.passwordUpdatedOn as LocalDate) < ?1 OR self.passwordUpdatedOn IS NULL",
                  date)
              .fetch();
    } else {
      if (CollectionUtils.isNotEmpty(passwordChangeBatch.getGroups())) {
        userList =
            userRepo
                .all()
                .filter(
                    "(cast(self.passwordUpdatedOn as LocalDate) < ?1 OR self.passwordUpdatedOn IS NULL) AND self.group IN (?2)",
                    date,
                    passwordChangeBatch.getGroups())
                .fetch();
      }
      if (CollectionUtils.isNotEmpty(passwordChangeBatch.getUsers())) {
        List<Long> userIdList =
            passwordChangeBatch.getUsers().stream().map(User::getId).collect(Collectors.toList());
        userList.addAll(
            userRepo
                .all()
                .filter(
                    "(cast(self.passwordUpdatedOn as LocalDate) < ?1 OR self.passwordUpdatedOn IS NULL) AND self.id IN (?2)",
                    date,
                    userIdList)
                .fetch());
      }
    }

    return new HashSet<User>(userList);
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void updateUsers(Set<User> userSet) {

    LocalDateTime todayDateTime = appBaseService.getTodayDateTime().toLocalDateTime();

    for (User user : userSet) {
      String password = userService.generateRandomPassword().toString();
      user.setTransientPassword(password);
      password = authService.encrypt(password);
      user.setPassword(password);
      user.setPasswordUpdatedOn(todayDateTime);
      user.setForcePasswordChange(true);
      userRepo.save(user);
      incrementDone();
    }

    User user = userService.getUser();

    // Update login date in session so that user changing own password doesn't get logged out.
    if (userSet.contains(user)) {
      Session session = AuthUtils.getSubject().getSession();
      session.setAttribute("com.axelor.internal.loginDate", todayDateTime);
    }
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void generateRandomPasswordForUsers(Set<User> userSet) {

    LocalDateTime todayDateTime = appBaseService.getTodayDateTime().toLocalDateTime();

    for (User user : userSet) {
      String password = userService.generateRandomPassword().toString();
      user.setTransientPassword(password);
      password = authService.encrypt(password);
      user.setPassword(password);
      user.setPasswordUpdatedOn(todayDateTime);
      userRepo.save(user);
      incrementDone();
    }

    User user = userService.getUser();

    // Update login date in session so that user changing own password doesn't get logged out.
    if (userSet.contains(user)) {
      Session session = AuthUtils.getSubject().getSession();
      session.setAttribute("com.axelor.internal.loginDate", todayDateTime);
    }
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void updatePasswordNextLoginForUsers(Set<User> userSet) {

    for (User user : userSet) {
      user.setForcePasswordChange(true);
      userRepo.save(user);
      incrementDone();
    }
  }

  @Override
  protected void stop() {

    String comment = String.format("%s Users processed", batch.getDone());

    super.stop();
    addComment(comment);
  }
}
