package com.axelor.apps.base.xml.adapters;

import com.axelor.auth.db.Group;
import com.axelor.auth.db.repo.GroupRepository;
import com.axelor.inject.Beans;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class GroupXmlAdapter extends XmlAdapter<String, Group> {

  public GroupXmlAdapter() {}

  @Override
  public String marshal(Group group) throws Exception {

    return group.getCode();
  }

  @Override
  public Group unmarshal(String code) throws Exception {

    return Beans.get(GroupRepository.class).findByCode(code);
  }
}
