package com.axelor.apps.base.xml.adapters;

import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.inject.Beans;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class UserXmlAdapter extends XmlAdapter<String, User> {

  public UserXmlAdapter() {}

  @Override
  public String marshal(User user) throws Exception {

    return user.getCode();
  }

  @Override
  public User unmarshal(String code) throws Exception {

    return Beans.get(UserRepository.class).findByCode(code);
  }
}
