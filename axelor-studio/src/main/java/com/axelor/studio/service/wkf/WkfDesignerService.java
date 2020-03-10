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
package com.axelor.studio.service.wkf;

import com.axelor.apps.tool.xml.XPathParse;
import com.axelor.auth.db.repo.RoleRepository;
import com.axelor.common.Inflector;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaField;
import com.axelor.studio.db.Wkf;
import com.axelor.studio.db.WkfNode;
import com.axelor.studio.db.WkfTransition;
import com.axelor.studio.db.repo.WkfNodeRepository;
import com.axelor.studio.db.repo.WkfRepository;
import com.axelor.studio.db.repo.WkfTransitionRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Handles bpmn xml processing of work-flow. Creates/removes work-flow nodes and transitions using
 * bpmn xml.
 *
 * @author axelor
 */
public class WkfDesignerService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected static final String WKF_STATUS = "wkfStatus";

  protected MetaField statusField = null;

  protected String dasherizeModel = null;

  protected String modelName = null;

  protected Inflector inflector;

  protected String defaultStatus = null;

  protected Set<WkfNode> nodes = new LinkedHashSet<>();

  protected Set<WkfTransition> transitions = new LinkedHashSet<>();

  private Wkf instance;

  private List<Integer> nodeSequences;

  @Inject protected RoleRepository roleRepo;

  @Inject private WkfRepository wkfRepo;

  @Inject private WkfService wkfService;

  /**
   * Parses xml doc to create workflow nodes from it. Sets incoming and outgoing transitions of
   * node.
   *
   * @param doc
   */
  public void traverseXMLElement(Document doc) {

    nodeSequences = new ArrayList<>();

    NodeList list = doc.getElementsByTagName("*");

    int nodeCount = 1;

    Map<String, WkfNode> nodeMap = getNodeMap();

    for (int i = 0; i < list.getLength(); i++) {

      Element element = (Element) list.item(i);

      if ((!element.getParentNode().getNodeName().equals("process"))
          || element.getNodeName().equals("sequenceFlow")) {
        continue;
      }

      String elementName = element.getTagName();

      WkfNode node = nodeMap.get(element.getAttribute("id"));

      if (node == null) {
        node = new WkfNode();
        node.setXmlId(element.getAttribute("id"));
        node.setName(element.getAttribute("name"));
        node.setTitle(node.getName());
        node.setWkf(instance);
        while (nodeSequences.contains(nodeCount)) {
          nodeCount += 10;
        }
        node.setSequence(nodeCount);
        nodeCount += 10;
        if (elementName.equals("startEvent")) node.setNodeType(WkfNodeRepository.START_NODE);
        else if (elementName.equals("endEvent")) node.setNodeType(WkfNodeRepository.END_NODE);
      } else {
        node.setName(element.getAttribute("name"));
        nodeMap.remove(node.getXmlId());
      }

      wkfService.clearNodes(nodeMap.values());
      NodeList incomings = element.getElementsByTagName("incoming");

      for (int j = 0; j < incomings.getLength(); j++) {
        Element incElement = (Element) incomings.item(j);
        String innerText = incElement.getTextContent();

        for (WkfTransition trn : transitions) {
          if (trn.getXmlId().equals(innerText)) {
            Set<WkfTransition> existIncomings = node.getIncoming();

            if (existIncomings == null) existIncomings = new HashSet<>();

            existIncomings.add(trn);
            node.setIncoming(existIncomings);
            trn.setTarget(node);
          }
        }
      }

      NodeList outgoings = element.getElementsByTagName("outgoing");

      for (int j = 0; j < outgoings.getLength(); j++) {
        Element outElement = (Element) outgoings.item(j);
        String innerText = outElement.getTextContent();

        for (WkfTransition trn : transitions) {
          if (trn.getXmlId().equals(innerText)) {
            Set<WkfTransition> existOutgoings = node.getOutgoing();

            if (existOutgoings == null) existOutgoings = new HashSet<>();

            existOutgoings.add(trn);
            node.setOutgoing(existOutgoings);
            trn.setSource(node);
          }
        }
      }
      nodes.add(node);
    }
  }

  protected Map<String, WkfNode> getNodeMap() {

    WkfNodeRepository wkfNodeRepository = Beans.get(WkfNodeRepository.class);

    Map<String, WkfNode> nodeMap = new HashMap<>();
    if (instance != null) {
      List<WkfNode> wkfNodes =
          wkfNodeRepository.all().filter("self.wkf.id = ?1", instance.getId()).fetch();
      for (WkfNode node : wkfNodes) {
        nodeMap.put(node.getXmlId(), node);
        nodeSequences.add(node.getSequence());
      }
    }

    return nodeMap;
  }

  protected Map<String, WkfTransition> getTransitionMap() {

    WkfTransitionRepository wkfTransitionRepo = Beans.get(WkfTransitionRepository.class);

    Map<String, WkfTransition> transitionMap = new HashMap<>();
    if (instance != null) {
      List<WkfTransition> wkfTransitions =
          wkfTransitionRepo.all().filter("self.wkf.id = ?1", instance.getId()).fetch();
      for (WkfTransition transition : wkfTransitions) {
        transitionMap.put(transition.getXmlId(), transition);
      }
    }

    return transitionMap;
  }

  /**
   * Fetches bpmn xml from workflow. Generates document from xml using dom parser. Generates
   * transitions from dom document and calls method to create nodes.
   *
   * @param instance Workflow instance
   * @throws ParserConfigurationException
   * @throws IOException
   * @throws SAXException
   * @throws Exception
   */
  @Transactional(rollbackOn = {Exception.class})
  public Wkf processXml(Wkf instance)
      throws ParserConfigurationException, SAXException, IOException {

    this.instance = instance;
    String bpmnXml = instance.getBpmnXml();
    if (bpmnXml != null) {
      DocumentBuilder db =
          Beans.get(XPathParse.class).getDocumentBuilderFactory().newDocumentBuilder();
      InputSource is = new InputSource();
      is.setCharacterStream(new StringReader(bpmnXml));

      Document doc = db.parse(is);

      NodeList list;

      Map<String, WkfTransition> transitionMap = getTransitionMap();
      list = doc.getElementsByTagName("sequenceFlow");
      for (int i = 0; i < list.getLength(); i++) {
        Element element = (Element) list.item(i);

        WkfTransition transition = transitionMap.get(element.getAttribute("id"));

        if (transition == null) {
          transition = new WkfTransition();
          transition.setXmlId(element.getAttribute("id"));
          transition.setName(element.getAttribute("name"));
          transition.setWkf(instance);
          log.debug(
              "New transition : {}, Version: {}", transition.getName(), transition.getVersion());
        } else {
          transition.setName(element.getAttribute("name"));
        }

        transitions.add(transition);
      }

      List<WkfNode> allRemoveNodes = instance.getNodes();

      if (!allRemoveNodes.isEmpty()) {
        for (WkfNode tempNode : allRemoveNodes) {
          tempNode.getIncoming().clear();
          tempNode.getOutgoing().clear();
        }
      }

      instance.getTransitions().clear();

      for (WkfTransition transition : transitions) {
        if (transition.getVersion() == null) {
          transition.setIsButton(true);
          transition.setButtonTitle(transition.getName());
          transition.setColSpan(transition.getColSpan());
        }
        instance.addTransition(transition);
      }

      traverseXMLElement(doc);

      instance.getNodes().clear();

      for (WkfNode node : nodes) {
        instance.addNode(node);
      }
    }

    return wkfRepo.save(instance);
  }
}
