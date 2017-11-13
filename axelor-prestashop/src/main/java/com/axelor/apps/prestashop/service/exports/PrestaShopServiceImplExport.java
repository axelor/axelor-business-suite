/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2017 Axelor (<http://axelor.com>).
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

package com.axelor.apps.prestashop.service.exports;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.xml.transform.TransformerException;

import org.apache.poi.hssf.record.ScenarioProtectRecord;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.axelor.apps.base.db.AppPrestashop;
import com.axelor.apps.base.db.Country;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PartnerAddress;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.ProductCategory;
import com.axelor.apps.base.db.repo.AppPrestashopRepository;
import com.axelor.apps.base.db.repo.CountryRepository;
import com.axelor.apps.base.db.repo.CurrencyRepository;
import com.axelor.apps.base.db.repo.PartnerAddressRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.ProductCategoryRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.prestashop.db.SaleOrderStatus;
import com.axelor.apps.prestashop.service.PSWebServiceClient;
import com.axelor.apps.prestashop.service.PrestaShopWebserviceException;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import edu.emory.mathcs.backport.java.util.Collections;

public class PrestaShopServiceImplExport {

	@Inject
	private MetaFiles metaFiles;

	@Inject
	private CountryRepository countryRepo;
	
	@Inject
	private CurrencyRepository currencyRepo;
	
	@Inject
	private PartnerRepository partnerRepo;
	
	@Inject
	private ProductCategoryRepository productCategoryRepo;
	
	@Inject
	private ProductRepository productRepo;
	
	@Inject
	private SaleOrderRepository saleOrderRepo;
	
	@Inject
	private SaleOrderLineRepository saleOrderLineRepo;

	File exportFile = File.createTempFile("Export Log", ".txt");
	FileWriter fwExport = null;
	BufferedWriter bwExport = null;
	Integer cnt = 0;

	private final String shopUrl;
	private final String key;
	private final boolean isStatus;
	private final List<SaleOrderStatus> saleOrderStatus;

	PSWebServiceClient ws;
	HashMap<String, Object> opt;
	Document schema;

	public PrestaShopServiceImplExport() throws IOException {

		AppPrestashop prestaShopObj = Beans.get(AppPrestashopRepository.class).all().fetchOne();
		shopUrl = prestaShopObj.getPrestaShopUrl();
		key = prestaShopObj.getPrestaShopKey();
		isStatus = prestaShopObj.getIsOrderStatus();
		saleOrderStatus = prestaShopObj.getSaleOrderStatus();
		ws = new PSWebServiceClient(shopUrl, key);
		fwExport = new FileWriter(exportFile);
		bwExport = new BufferedWriter(fwExport);
	}

	public void exportLogObjectHeder(String objectName) throws IOException {
		bwExport.newLine();
		bwExport.write("--------------");
		bwExport.newLine();
		bwExport.write(objectName + " object");
		bwExport.newLine();
		bwExport.write("--------------");
	}

	public void exportLog(String id, String msg) throws IOException {
		bwExport.newLine();
		bwExport.write("Id - " + id + " " + msg);
	}

	public File closeLog() throws IOException {
		bwExport.close();
		fwExport.close();

		return exportFile;
	}

	public void exportNewConnection(String apiName) throws PrestaShopWebserviceException {
		ws = new PSWebServiceClient(shopUrl + "/api/" + apiName + "?schema=blank", key);
		opt = new HashMap<String, Object>();
		opt.put("url", shopUrl + "/api/" + apiName + "?schema=blank");
		schema = ws.get(opt);
	}

	public void exportUpdateConnection(String resource, String id) throws PrestaShopWebserviceException {
		ws = new PSWebServiceClient(shopUrl, key);
		opt = new HashMap<String, Object>();
		opt.put("resource", resource);
		opt.put("id", id);
		schema = ws.get(opt);
	}

	public Document addRecord(String resource, Document schemaObj)
			throws TransformerException, PrestaShopWebserviceException {
		opt.put("resource", resource);
		opt.put("postXml", ws.DocumentToString(schemaObj));
		Document document = ws.add(opt);
		return document;
	}

	public void updatRecord(String resource, Document schemaObj, String id)
			throws PrestaShopWebserviceException, TransformerException {
		HashMap<String, Object> updateOpt = new HashMap<String, Object>();
		updateOpt.put("resource", resource);
		updateOpt.put("putXml", ws.DocumentToString(schemaObj));
		updateOpt.put("id", id);
		ws.edit(updateOpt);
	}

	public List<String> fetchApiIds(String resources, String node) throws PrestaShopWebserviceException {

		PSWebServiceClient ws = new PSWebServiceClient(shopUrl, key);
		opt = new HashMap<String, Object>();
		opt.put("resource", resources);
		Document schema = ws.get(opt);
		NodeList nodeList = schema.getElementsByTagName(node);
		List<String> ids = new ArrayList<String>();

		for (int x = 0, size = nodeList.getLength(); x < size; x++) {
			ids.add(nodeList.item(x).getAttributes().getNamedItem("id").getNodeValue());
		}
		return ids;
	}
	
	public void addImages(MetaFile productPicture)
			throws PrestaShopWebserviceException, TransformerException, MalformedURLException, IOException {

		ws = new PSWebServiceClient(shopUrl, key);
		opt = new HashMap<String, Object>();
		opt.put("resource", "products");
		schema = ws.get(opt);
		NodeList productsNodeList = schema.getElementsByTagName("product");

		List<Long> sortIds = new ArrayList<>();
		for (int x = 0; x < productsNodeList.getLength(); x++) {
			sortIds.add(Long.parseLong(productsNodeList.item(x).getAttributes().getNamedItem("id").getNodeValue()));
		}
		Collections.sort(sortIds);
		Integer productId = Integer.parseInt(sortIds.get(sortIds.size() - 1).toString());

		Path path = metaFiles.getPath(productPicture);
		ws = new PSWebServiceClient(shopUrl, key);
		ws.addImg(path.toUri().toString(), productId);
	}

	public String fetchCategories(String catName) throws PrestaShopWebserviceException {

		String cat_id = "";
		List<String> categoriesIds = fetchApiIds("categories", "category");

		for (String id : categoriesIds) {
			PSWebServiceClient ws = new PSWebServiceClient(shopUrl + "/api/categories/" + id, key);
			opt = new HashMap<String, Object>();
			opt.put("resource", "categories");
			Document catSchema = ws.get(opt);
			NodeList list = catSchema.getChildNodes();

			for (int i = 0; i < list.getLength(); i++) {
				if (list.item(i).getNodeType() == Node.ELEMENT_NODE) {
					Element element = (Element) list.item(i);
					if (element.getElementsByTagName("name").item(0).getTextContent().toString().equals(catName)) {
						cat_id = element.getElementsByTagName("id").item(0).getTextContent().toString();
					}
				}
			}
		}
		return cat_id;
	}
	
	public String isCurrency(String currencyCode) throws PrestaShopWebserviceException {
		
		String prestaShopId = null;
		PSWebServiceClient ws = new PSWebServiceClient(shopUrl, key);
		HashMap<String, String> currencyMap = new HashMap<String, String>();
		currencyMap.put("iso_code", currencyCode);
		opt = new HashMap<String, Object>();
		opt.put("resource", "currencies");
		opt.put("filter", currencyMap);
		Document str =  ws.get(opt);
		
		NodeList list = str.getElementsByTagName("currencies");
		for(int i = 0; i < list.getLength(); i++) {
		    Element element = (Element) list.item(i);
		    NodeList node = element.getElementsByTagName("currency");
		    Node currency = node.item(i);
		    if(node.getLength() > 0) {
		    	prestaShopId = currency.getAttributes().getNamedItem("id").getNodeValue();
		    	return prestaShopId;
		    }
		}
		return prestaShopId;
	}
	
	public String isCountry(String countryCode) throws PrestaShopWebserviceException {
		
		String prestaShopId = null;
		PSWebServiceClient ws = new PSWebServiceClient(shopUrl, key);
		HashMap<String, String> countryMap = new HashMap<String, String>();
		countryMap.put("iso_code", countryCode);
		opt = new HashMap<String, Object>();
		opt.put("resource", "countries");
		opt.put("filter", countryMap);
		Document str =  ws.get(opt);
		
		NodeList list = str.getElementsByTagName("countries");
		for(int i = 0; i < list.getLength(); i++) {
		    Element element = (Element) list.item(i);
		    NodeList node = element.getElementsByTagName("country");
		    Node country = node.item(i);
		    if(node.getLength() > 0) {
		    	prestaShopId = country.getAttributes().getNamedItem("id").getNodeValue();
		    	return prestaShopId;
		    }
		}
		return prestaShopId;
	}
	
	public void exportResetOrderDetails(String orderId) throws PrestaShopWebserviceException {
		
		String orderDetailId = null;
		ws = new PSWebServiceClient(shopUrl, key);
		HashMap<String, String> orderDetailMap = new HashMap<String, String>();
		orderDetailMap.put("id_order", orderId);
		opt = new HashMap<String, Object>();
		opt.put("resource", "order_details");
		opt.put("filter", orderDetailMap);
		Document str =  ws.get(opt);
		
		NodeList list = str.getElementsByTagName("order_details");
		for(int i = 0; i < list.getLength(); i++) {
		    Element element = (Element) list.item(i);
		    NodeList nodeList = element.getElementsByTagName("order_detail");
		    for(int j = 0; j < nodeList.getLength(); j++) {
		    	Node order = nodeList.item(j);
		    	
		    	if(nodeList.getLength() > 0) {
		    		orderDetailId =  order.getAttributes().getNamedItem("id").getNodeValue();
		    		PSWebServiceClient ws = new PSWebServiceClient(shopUrl, key);
			    	HashMap<String, Object> opt  = new HashMap<String, Object>();
			    	opt.put("resource", "order_details");
			    	opt.put("id", orderDetailId);
			    	ws.delete(opt);
			    }
		    	
		    }
		}
	}
	
	@SuppressWarnings("deprecation")
	public Partner addPartner(Partner partner) throws IOException {
		
		try {

			if (partner.getPartnerTypeSelect() == 1) {

				if (partner.getContactPartnerSet().size() != 0) {
					schema.getElementsByTagName("company").item(0).setTextContent(partner.getName());
					
					if(!partner.getContactPartnerSet().iterator().next().getFirstName().isEmpty() && !partner.getContactPartnerSet().iterator().next().getName().isEmpty()) {
						schema.getElementsByTagName("firstname").item(0).setTextContent(partner.getContactPartnerSet().iterator().next().getFirstName());
						schema.getElementsByTagName("lastname").item(0).setTextContent(partner.getContactPartnerSet().iterator().next().getName());
					} else {
						throw new AxelorException(String.format("Contact is null or invalid"), IException.NO_VALUE);
					}
					
				} else {
					throw new AxelorException(String.format("Contact is null or invalid"), IException.NO_VALUE);
				}

			} else {
				
				if (!partner.getName().isEmpty() && !partner.getFirstName().isEmpty()) {
					schema.getElementsByTagName("firstname").item(0).setTextContent(partner.getFirstName());
					schema.getElementsByTagName("lastname").item(0).setTextContent(partner.getName());
				} else {
					throw new AxelorException(String.format("Individual is null or invalid"),IException.NO_VALUE);
				}
			}
				
			if (partner.getPaymentCondition() != null) {
				schema.getElementsByTagName("max_payment_days").item(0)
						.setTextContent(partner.getPaymentCondition().getPaymentTime().toString());
			}

			if (partner.getEmailAddress() != null) {
				schema.getElementsByTagName("email").item(0).setTextContent(partner.getEmailAddress().getAddress());
			} else {
				throw new AxelorException(String.format("Email is null or invalid"), IException.NO_VALUE);
			}
			
			schema.getElementsByTagName("id_gender").item(0).setTextContent(partner.getTitleSelect().toString());
			schema.getElementsByTagName("id_default_group").item(0).setTextContent("3");
			schema.getElementsByTagName("website").item(0).setTextContent(partner.getWebSite());
			schema.getElementsByTagName("active").item(0).setTextContent("1");
			
		} catch (AxelorException e) {
			cnt++;
			exportLog(partner.getId().toString(), e.getMessage());
		}
		
		
		return partner;
	}
	
	public String getCustomerId() throws PrestaShopWebserviceException {
		
		ws = new PSWebServiceClient(shopUrl, key);
		opt = new HashMap<String, Object>();
		opt.put("resource", "customers");
		schema = ws.get(opt);
		NodeList customersNodeList = schema.getElementsByTagName("customer");
		List<Long> sortIds = new ArrayList<>();

		for (int x = 0; x < customersNodeList.getLength(); x++) {
			sortIds.add(Long.parseLong(customersNodeList.item(x).getAttributes().getNamedItem("id").getNodeValue()));
		}

		Collections.sort(sortIds);
		return sortIds.get(sortIds.size() - 1).toString();
	}
	
	public ProductCategory addCategory(ProductCategory category) throws PrestaShopWebserviceException {
		
		String id_parent = "";
		schema.getElementsByTagName("active").item(0).setTextContent("1");
		if (category.getParentProductCategory() == null || category.getParentProductCategory().getPrestaShopId().equals("1") || category.getParentProductCategory().getPrestaShopId().equals("1")) {
			schema.getElementsByTagName("id_parent").item(0).setTextContent("2");
		} else {
			id_parent = category.getParentProductCategory().getPrestaShopId();
			schema.getElementsByTagName("id_parent").item(0).setTextContent(id_parent);
		}
		
		schema.getElementsByTagName("active").item(0).setTextContent("1");
		
		Element name = (Element) schema.getElementsByTagName("name").item(0).getFirstChild();
		name.setTextContent(null);
		name.appendChild(schema.createCDATASection(category.getName()));
		name.setAttribute("id", "1");
		name.setAttribute("xlink:href", shopUrl + "/api/languages/" + 1);

		Element link_rewrite = (Element) schema.getElementsByTagName("link_rewrite").item(0)
				.getFirstChild();
		link_rewrite.setTextContent(null);
		link_rewrite.appendChild(schema.createCDATASection(category.getCode()));
		link_rewrite.setAttribute("id", "1");
		link_rewrite.setAttribute("xlink:href", shopUrl + "/api/languages/" + 1);
		
		return category;
				
	}
	
	public Product addProduct(Product product) {
		
		ProductCategory productCategory = productCategoryRepo.findByName(product.getProductCategory().getName());
		String prestaShopCategoryId = productCategory.getPrestaShopId().toString();
		
		schema.getElementsByTagName("id_category_default").item(0).setTextContent(prestaShopCategoryId);
		
		Node categoriesRows = schema.getElementsByTagName("categories").item(0);
		Node defaultRow;
		Integer totalCategoryRow = schema.getElementsByTagName("category").getLength();

		for (int x = 0; x < totalCategoryRow; x++) {
			defaultRow = schema.getElementsByTagName("category").item(0);
			categoriesRows.removeChild(defaultRow);
		}

		Element categoryRow = schema.createElement("category");
		Element id = schema.createElement("id");
		id.setTextContent(null);
		id.setTextContent(prestaShopCategoryId);
		categoryRow.appendChild(id);
		categoriesRows.appendChild(categoryRow);
		
		schema.getElementsByTagName("price").item(0).setTextContent(product.getSalePrice().setScale(0, RoundingMode.HALF_UP).toString());
		schema.getElementsByTagName("width").item(0).setTextContent(product.getWidth().toString());
		schema.getElementsByTagName("minimal_quantity").item(0).setTextContent("2");
		schema.getElementsByTagName("on_sale").item(0).setTextContent("0");
		schema.getElementsByTagName("active").item(0).setTextContent("1");
		schema.getElementsByTagName("available_for_order").item(0).setTextContent("1");
		schema.getElementsByTagName("show_price").item(0).setTextContent("1");
		schema.getElementsByTagName("state").item(0).setTextContent("1");
		schema.getElementsByTagName("name").item(0).getFirstChild().setTextContent(product.getName());

		if (product.getDescription() != null) {
			schema.getElementsByTagName("description").item(0).getFirstChild().setTextContent(product.getDescription());
		}

		if (product.getProductTypeSelect() != null) {
			schema.getElementsByTagName("link_rewrite").item(0).getFirstChild().setTextContent(product.getProductTypeSelect().toString());
		}
		
		return product;
	}
	
	public void addStock(String id, String quantity) throws PrestaShopWebserviceException, TransformerException {
		
		PSWebServiceClient ws = new PSWebServiceClient(shopUrl, key);
		HashMap<String, Object> opt = new HashMap<String, Object>();
		opt.put("resource", "stock_availables");
		opt.put("id", id);
		Document stockSchema = ws.get(opt);
		
			stockSchema.getElementsByTagName("quantity").item(0).setTextContent(quantity);
			
		HashMap<String, Object> updateOpt = new HashMap<String, Object>();
		updateOpt.put("resource", "stock_availables");
		updateOpt.put("putXml", ws.DocumentToString(stockSchema));
		updateOpt.put("id", id);
		ws.edit(updateOpt);	
			
	}
	
	public String getCartId(SaleOrder saleOrder) throws PrestaShopWebserviceException {
			
		String cart_id = "";

			ws = new PSWebServiceClient(shopUrl + "/api/orders/" + saleOrder.getPrestaShopId(),key);
			opt = new HashMap<String, Object>();
			opt.put("resource", "orders");
			schema = ws.get(opt);
			NodeList list = schema.getChildNodes();

			for (int i = 0; i < list.getLength(); i++) {
				if (list.item(i).getNodeType() == Node.ELEMENT_NODE) {
					Element element = (Element) list.item(i);
					if (element.getElementsByTagName("id").item(0).getTextContent().toString()
							.equals(saleOrder.getPrestaShopId())) {
						cart_id = element.getElementsByTagName("id_cart").item(0).getTextContent().toString();
					}
				}
			}
		
		return cart_id;
	}
	
	public void addCartProduct(SaleOrder saleOrder, String[] saleOrder_product_id, String id_address_delivery) {
		
		Node cartRows = schema.getElementsByTagName("cart_rows").item(0);
		Node defaultRow;
		Integer totalCartRow = schema.getElementsByTagName("cart_row").getLength();

		for (int x = 0; x < totalCartRow; x++) {
			defaultRow = schema.getElementsByTagName("cart_row").item(0);
			cartRows.removeChild(defaultRow);
		}

		for (int j = 0; j < saleOrder_product_id.length; j++) {
			
			if(saleOrder_product_id[j] != null) {
				Element cartRow = schema.createElement("cart_row");
				Element idProduct = schema.createElement("id_product");
				Element id_product_attribute = schema.createElement("id_product_attribute");
				Element idAddressDelivery = schema.createElement("id_address_delivery");
				Element quantity = schema.createElement("quantity");

				idProduct.setTextContent(saleOrder_product_id[j]);
				idAddressDelivery.setTextContent(id_address_delivery);
				quantity.setTextContent(
						saleOrder.getSaleOrderLineList().get(j).getQty().toString());

				cartRow.appendChild(idProduct);
				cartRow.appendChild(id_product_attribute);
				cartRow.appendChild(idAddressDelivery);
				cartRow.appendChild(quantity);
				cartRows.appendChild(cartRow);
			}
		}
	}
	
	public void createOrder(SaleOrder saleOrder, String id_address_delivery, String id_address_invoice,
								String cartId, String cart_id, String id_currency, String id_customer, String[] saleOrder_product_id, ZonedDateTime endDate) throws PrestaShopWebserviceException, TransformerException {
		
		if (saleOrder.getPrestaShopId() == null) {
			exportNewConnection("orders");
		} else {
			exportUpdateConnection("orders", saleOrder.getPrestaShopId());
		}

		schema.getElementsByTagName("id_address_delivery").item(0)
				.setTextContent(id_address_delivery);
		schema.getElementsByTagName("id_address_invoice").item(0)
				.setTextContent(id_address_invoice);

		if (saleOrder.getPrestaShopId() == null) {
			schema.getElementsByTagName("id_cart").item(0).setTextContent(cartId);
		} else {
			schema.getElementsByTagName("id_cart").item(0).setTextContent(cart_id);
		}

		schema.getElementsByTagName("id_currency").item(0).setTextContent(id_currency);
		schema.getElementsByTagName("id_lang").item(0).setTextContent("1");
		schema.getElementsByTagName("id_customer").item(0).setTextContent(id_customer);
		schema.getElementsByTagName("id_carrier").item(0).setTextContent("1");
		schema.getElementsByTagName("total_paid_tax_incl").item(0).setTextContent(saleOrder.getExTaxTotal().setScale(2, RoundingMode.HALF_UP).toString());
		schema.getElementsByTagName("total_wrapping_tax_incl").item(0).setTextContent(saleOrder.getTaxTotal().setScale(2, RoundingMode.HALF_UP).toString());
		schema.getElementsByTagName("total_paid").item(0).setTextContent(saleOrder.getInTaxTotal().setScale(2, RoundingMode.HALF_UP).toString());
		schema.getElementsByTagName("total_paid_tax_excl").item(0).setTextContent(saleOrder.getExTaxTotal().setScale(2, RoundingMode.HALF_UP).toString());
		schema.getElementsByTagName("total_paid_real").item(0).setTextContent(saleOrder.getExTaxTotal().setScale(2, RoundingMode.HALF_UP).toString());
		schema.getElementsByTagName("total_products_wt").item(0).setTextContent(saleOrder.getExTaxTotal().setScale(2, RoundingMode.HALF_UP).toString());
		schema.getElementsByTagName("total_shipping").item(0).setTextContent("0");
		schema.getElementsByTagName("total_products").item(0).setTextContent(saleOrder.getExTaxTotal().setScale(2, RoundingMode.HALF_UP).toString());
		schema.getElementsByTagName("total_wrapping_tax_incl").item(0).setTextContent("00.0");
		schema.getElementsByTagName("total_shipping_tax_incl").item(0).setTextContent("00.0");
		schema.getElementsByTagName("total_shipping_tax_excl").item(0).setTextContent("00.0");
		schema.getElementsByTagName("conversion_rate").item(0).setTextContent("0.00");
		schema.getElementsByTagName("module").item(0).setTextContent("ps_checkpayment");
		schema.getElementsByTagName("payment").item(0).setTextContent(saleOrder.getPaymentCondition().getName());
			
		if(saleOrder.getPrestaShopId() == null) {
			
			Node orderRows = schema.getElementsByTagName("order_rows").item(0);
			Node defaultOrderRow;
			Integer totalOrderRow = schema.getElementsByTagName("order_row").getLength();

			for (int x = 0; x < totalOrderRow; x++) {
				defaultOrderRow = schema.getElementsByTagName("order_row").item(0);
				orderRows.removeChild(defaultOrderRow);
			}

			for (int j = 0; j < saleOrder_product_id.length; j++) {

				if(saleOrder_product_id[j] != null) {
					Element orderRow = schema.createElement("order_row");
					Element product_id = schema.createElement("product_id");
					product_id.setTextContent(saleOrder_product_id[j]);
					orderRow.appendChild(product_id);
					orderRows.appendChild(orderRow);
				}
			}
			
			Document document = addRecord("orders", schema);
			String orderId = document.getElementsByTagName("id").item(0).getTextContent();
			saleOrder.setPrestaShopId(orderId);
			exportNewConnection("order_histories");
			schema.getElementsByTagName("id_order").item(0).setTextContent(orderId);
			
		} else {
			
			updatRecord("orders", schema, saleOrder.getPrestaShopId());
			exportNewConnection("order_histories");
			schema.getElementsByTagName("id_order").item(0).setTextContent(saleOrder.getPrestaShopId());
		}
		
		for(SaleOrderStatus orderStatus : saleOrderStatus) {
			if(orderStatus.getAbsStatus() == saleOrder.getStatusSelect()) {
				schema.getElementsByTagName("id_order_state").item(0).setTextContent(orderStatus.getPrestaShopStatus().toString());
				break;
			}
		}
		opt.put("resource", "db_order_history");
		opt.put("postXml", ws.DocumentToString(schema));
		ws.add(opt);
		saleOrderRepo.save(saleOrder);
	}
	
	public List<String> getOrderDetailsIds(String orderId) throws PrestaShopWebserviceException {
		
		List<String> orderDetailsIds = new ArrayList<String>();
		PSWebServiceClient ws = new PSWebServiceClient(shopUrl, key);
		HashMap<String, String> orderDetailMap = new HashMap<String, String>();
		orderDetailMap.put("id_order", orderId);
		opt = new HashMap<String, Object>();
		opt.put("resource", "order_details");
		opt.put("filter", orderDetailMap);
		Document str =  ws.get(opt);
		
		NodeList list = str.getElementsByTagName("order_details");
		for(int i = 0; i < list.getLength(); i++) {
		    Element element = (Element) list.item(i);
		    NodeList nodeList = element.getElementsByTagName("order_detail");
		    for(int j = 0; j < nodeList.getLength(); j++) {
		    	Node order_detail = nodeList.item(j);
		    	
		    	if(nodeList.getLength() > 0) {
		    		orderDetailsIds.add(order_detail.getAttributes().getNamedItem("id").getNodeValue());
			    }
		    	
		    }
		}
		return orderDetailsIds;
	}
	
	@Transactional
	public void updateOrderStatus(SaleOrder saleOrder, String orderId) throws PrestaShopWebserviceException, TransformerException {
		
		exportNewConnection("order_histories");
		schema.getElementsByTagName("id_order").item(0).setTextContent(orderId);
		schema.getElementsByTagName("id_order_state").item(0).setTextContent("1");
		opt.put("resource", "db_order_history");
		opt.put("postXml", ws.DocumentToString(schema));
		ws.add(opt);
		
		saleOrderRepo.save(saleOrder);
	}
	
	@SuppressWarnings("deprecation")
	@Transactional
	public void exportAxelorCurrencies(ZonedDateTime endDate) throws IOException, PrestaShopWebserviceException, TransformerException {

		this.exportLogObjectHeder("Currency");
		List<Currency> currencies = null;
		
		if(endDate == null) {
			currencies = Beans.get(CurrencyRepository.class).all().fetch();
		} else {
			currencies = Beans.get(CurrencyRepository.class).all().filter("self.createdOn > ?1 OR self.updatedOn > ?2 OR self.prestaShopId = null", endDate, endDate).fetch();
		}

		for (Currency currency : currencies) {
				try {
					String prestaShopId = this.isCurrency(currency.getCode()); 
					
					if (currency.getPrestaShopId() == null) {
						if(prestaShopId != null) {
							this.exportUpdateConnection("currencies", prestaShopId);
							currency.setPrestaShopId(prestaShopId);
						} else {
							this.exportNewConnection("currencies");
						}
						
					} else {
						this.exportUpdateConnection("currencies", currency.getPrestaShopId());
					}

					if (currency.getCode() != null && currency.getName() != null) {
						schema.getElementsByTagName("name").item(0).setTextContent(currency.getName());
						schema.getElementsByTagName("iso_code").item(0).setTextContent(currency.getCode());
						schema.getElementsByTagName("conversion_rate").item(0).setTextContent("1.00");
						schema.getElementsByTagName("deleted").item(0).setTextContent("0");
						schema.getElementsByTagName("active").item(0).setTextContent("1");
						
					} else {
						throw new AxelorException(String.format("Currency code /name is null or invalid"),IException.NO_VALUE);
					}

					if (currency.getPrestaShopId() == null) {
						Document document = this.addRecord("currencies", schema);
						currency.setPrestaShopId(document.getElementsByTagName("id").item(0).getTextContent());
						currencyRepo.save(currency);
					} else {
						this.updatRecord("currencies", schema, currency.getPrestaShopId());
					}
					currencyRepo.save(currency);
					
				} catch (AxelorException e) {
					this.exportLog(currency.getId().toString(), e.getMessage());
					continue;
				} catch (Exception e) {
					this.exportLog(currency.getId().toString(), e.getMessage());
					continue;
				}
			}
	}
	
	@SuppressWarnings("deprecation")
	public void exportAxelorCountries(ZonedDateTime endDate) throws PrestaShopWebserviceException, IOException {
		
		this.exportLogObjectHeder("Country");
		List<Country> countries = null;
		
		if(endDate == null) {
			countries = Beans.get(CountryRepository.class).all().fetch();
		} else {
			countries = Beans.get(CountryRepository.class).all().filter("self.createdOn > ?1 OR self.updatedOn > ?2 OR self.prestaShopId = null", endDate, endDate).fetch();
		}
		
		for(Country country : countries) {
			try {
				
				String prestaShopId = this.isCountry(country.getAlpha2Code());
				
				if(prestaShopId != null) {
					this.exportUpdateConnection("countries", prestaShopId);
				} else {
					this.exportNewConnection("countries");
				}
				
				if(country.getName() != null) {
					schema.getElementsByTagName("name").item(0).getFirstChild().setTextContent(country.getName());
					schema.getElementsByTagName("iso_code").item(0).setTextContent(country.getAlpha2Code());
					schema.getElementsByTagName("id_zone").item(0).setTextContent("1");
					schema.getElementsByTagName("contains_states").item(0).setTextContent("0");
					schema.getElementsByTagName("need_identification_number").item(0).setTextContent("0");
					schema.getElementsByTagName("display_tax_label").item(0).setTextContent("1");
					schema.getElementsByTagName("active").item(0).setTextContent("1");
				} else {
					throw new AxelorException(String.format("Country is null or invalid"), IException.NO_VALUE);		
				}

				if (prestaShopId == null) {
					Document document = this.addRecord("countries", schema);
					country.setPrestaShopId(document.getElementsByTagName("id").item(0).getTextContent());
				} else {
					country.setPrestaShopId(prestaShopId);
					this.updatRecord("countries", schema, country.getPrestaShopId());
				}
				countryRepo.save(country);
				
			} catch (AxelorException e) {
				this.exportLog(country.getId().toString(), e.getMessage());
				continue;
			} catch (Exception e) {
				this.exportLog(country.getId().toString(), e.getMessage());
				continue;
			}
		}
	}

	@Transactional
	public void exportAxelorPartners(ZonedDateTime endDate) throws IOException, PrestaShopWebserviceException {

		this.exportLogObjectHeder("Partner");
		String prestaShopId = null;
		List<Partner> partners = null;
		
		if(endDate == null) {
			partners = Beans.get(PartnerRepository.class).all().filter("isCustomer = true").fetch();
		} else {
			partners = Beans.get(PartnerRepository.class).all().filter("isCustomer = true AND (self.createdOn > ?1 OR self.updatedOn > ?2 OR self.prestaShopId = null)", endDate, endDate).fetch();
		}
		
			for (Partner partnerObj : partners) {
				
				try {
					if (partnerObj.getPrestaShopId() == null) {
						this.exportNewConnection("customers");
					} else {
						this.exportUpdateConnection("customers", partnerObj.getPrestaShopId());
					}

					partnerObj = this.addPartner(partnerObj);
					
					if (partnerObj.getPrestaShopId() == null) {
						Document document = this.addRecord("customers", schema);
						prestaShopId = document.getElementsByTagName("id").item(0).getTextContent();
						partnerObj.setPrestaShopId(prestaShopId);
						partnerRepo.save(partnerObj);
					} else {
						this.updatRecord("customers", schema, partnerObj.getPrestaShopId());
					}

				} catch (Exception e) {
					if(cnt > 0) {
                        cnt = 0;
                        continue;
                    }
					this.exportLog(partnerObj.getId().toString(), e.getMessage());
					continue;
				}
			}
	}
	
	@SuppressWarnings("deprecation")
	@Transactional
	public void exportAxelorPartnerAddresses(ZonedDateTime endDate) throws TransformerException, PrestaShopWebserviceException, IOException {
		
		this.exportLogObjectHeder("Address");
		List<PartnerAddress> partnerAddresses = null;
		
		if(endDate == null) {
			partnerAddresses = Beans.get(PartnerAddressRepository.class).all().filter("self.partner.prestaShopId != null").fetch();
		} else {
			partnerAddresses = Beans.get(PartnerAddressRepository.class).all().filter("(self.createdOn > ?1 OR self.updatedOn > ?2 OR self.address.updatedOn > ?3 OR self.address.prestaShopId = null) AND self.partner.prestaShopId != null", endDate, endDate, endDate).fetch();
		}
		
			for (PartnerAddress partnerAddress : partnerAddresses) {
				
				try {
						if (partnerAddress.getAddress().getPrestaShopId() == null) {
							this.exportNewConnection("addresses");
						} else {
							this.exportUpdateConnection("addresses", partnerAddress.getAddress().getPrestaShopId());
						}
						
						schema.getElementsByTagName("id_customer").item(0).setTextContent(partnerAddress.getPartner().getPrestaShopId());
						
						if(partnerAddress.getPartner().getPartnerTypeSelect() == 1) {
							
							if(!partnerAddress.getPartner().getContactPartnerSet().isEmpty()) {
								
								if (partnerAddress.getPartner().getContactPartnerSet().iterator().next().getName() != null && 
										partnerAddress.getPartner().getContactPartnerSet().iterator().next().getFirstName() != null) {
									
									schema.getElementsByTagName("company").item(0).setTextContent(partnerAddress.getPartner().getName());
									schema.getElementsByTagName("firstname").item(0).setTextContent(partnerAddress.getPartner().getContactPartnerSet().iterator().next().getFirstName());
									schema.getElementsByTagName("lastname").item(0).setTextContent(partnerAddress.getPartner().getContactPartnerSet().iterator().next().getName());
									
								} else {
									throw new AxelorException(String.format("Company/Firstname is null or invalid"),IException.NO_VALUE);
								}
							} else {
								throw new AxelorException(String.format("Contact is null or invalid"),IException.NO_VALUE);
							}
							
						} else {
							if (partnerAddress.getPartner().getName() != null && partnerAddress.getPartner().getFirstName() != null) {
								schema.getElementsByTagName("firstname").item(0).setTextContent(partnerAddress.getPartner().getFirstName());
								schema.getElementsByTagName("lastname").item(0).setTextContent(partnerAddress.getPartner().getName());
							} else {
								throw new AxelorException(String.format("Company/Firstname is null or invalid"),IException.NO_VALUE);
							}
						}
						
						schema.getElementsByTagName("id_country").item(0).setTextContent(partnerAddress.getAddress().getAddressL7Country().getPrestaShopId());
						schema.getElementsByTagName("alias").item(0).setTextContent("Main Addresses");
						
						if (partnerAddress.getAddress().getCity() != null) {
							
							String postCode = null;
							String addString = partnerAddress.getAddress().getAddressL6();
							String[] words = addString.split("\\s");
							
							if(partnerAddress.getAddress().getCity().getHasZipOnRight()) {
								postCode = words[1];
							} else {
								postCode = words[0];
							}
							
							schema.getElementsByTagName("address1").item(0).setTextContent(partnerAddress.getAddress().getAddressL4());
							schema.getElementsByTagName("address2").item(0).setTextContent(partnerAddress.getAddress().getAddressL5());
							schema.getElementsByTagName("postcode").item(0).setTextContent(postCode);
							schema.getElementsByTagName("city").item(0).setTextContent(partnerAddress.getAddress().getCity().getName());
						} else {
							throw new AxelorException(String.format("City is null"), IException.NO_VALUE);
						}

						if (partnerAddress.getAddress().getPrestaShopId() == null) {
							Document document = this.addRecord("addresses", schema);
							partnerAddress.getAddress().setPrestaShopId(document.getElementsByTagName("id").item(0).getTextContent());
							partnerRepo.save(partnerAddress.getPartner());
						} else {
							this.updatRecord("addresses", schema, partnerAddress.getAddress().getPrestaShopId());
						}
						
					} catch (AxelorException e) {
						this.exportLog(partnerAddress.getAddress().getId().toString(), e.getMessage());
					}
			}
		}
	
	@SuppressWarnings("deprecation")
	@Transactional
	public void exportAxelorProductCategories(ZonedDateTime endDate) throws IOException, PrestaShopWebserviceException, TransformerException {

		this.exportLogObjectHeder("Product Category");
		List<ProductCategory> categories = null;
		
		if(endDate == null) {
			categories = Beans.get(ProductCategoryRepository.class).all().fetch();
		} else {
			categories = Beans.get(ProductCategoryRepository.class).all().filter("self.createdOn > ?1 OR self.updatedOn > ?2 OR self.prestaShopId = null", endDate, endDate).fetch();
		}
		
		for (ProductCategory category : categories) {
				
				try {
					
					if (category.getPrestaShopId() == null) {
						this.exportNewConnection("categories");
					} else {
						if(category.getPrestaShopId().equals("1") || category.getPrestaShopId().equals("2")) {
							continue;
						}
						this.exportUpdateConnection("categories", category.getPrestaShopId());
						Node categoryRoot = schema.getElementsByTagName("category").item(0);
						Node level_depth = schema.getElementsByTagName("level_depth").item(0);
						Node nb_products_recursive = schema.getElementsByTagName("nb_products_recursive").item(0);
						categoryRoot.removeChild(level_depth);
						categoryRoot.removeChild(nb_products_recursive);
					}

					if (!category.getName().equals("") && !category.getCode().equals("")) {

						category = this.addCategory(category);
						if (category.getPrestaShopId() == null) {
							Document document = this.addRecord("categories", schema);
							category.setPrestaShopId(document.getElementsByTagName("id").item(0).getTextContent());
							productCategoryRepo.save(category);
						} else {
							this.updatRecord("categories", schema, category.getPrestaShopId());
						}

					} else {
						throw new AxelorException(String.format("Product Category is null or invalid"),	IException.NO_VALUE);
					}

				} catch (AxelorException e) {
					this.exportLog(category.getId().toString(), e.getMessage());
					continue;

				} catch (Exception e) {
					this.exportLog(category.getId().toString(), e.getMessage());
					continue;
				}
		}
	}

	@SuppressWarnings("deprecation")
	@Transactional
	public void exportAxelorProducts(ZonedDateTime endDate) throws PrestaShopWebserviceException, IOException, TransformerException {
		
		this.exportLogObjectHeder("Product");
		List<Product> products = null;
		
		if(endDate == null) {
			products = Beans.get(ProductRepository.class).all().fetch();
		} else {
			products = Beans.get(ProductRepository.class).all().filter("self.createdOn > ?1 OR self.updatedOn > ?2 OR self.prestaShopId = null", endDate, endDate).fetch();
		}
		
			for (Product product : products) {
				
				try {
					if (product.getPrestaShopId() == null) {
						this.exportNewConnection("products");
					} else {
						exportUpdateConnection("products", product.getPrestaShopId());
						Node productRoot = schema.getElementsByTagName("product").item(0);
						Node manufacturer_name = schema.getElementsByTagName("manufacturer_name").item(0);
						Node quantity = schema.getElementsByTagName("quantity").item(0);
						productRoot.removeChild(manufacturer_name);
						productRoot.removeChild(quantity);
					}

					if (!product.getName().equals("")) {
						this.addProduct(product);
						
						if (product.getPrestaShopId() == null) {
							Document document = addRecord("products", schema);
							product.setPrestaShopId(document.getElementsByTagName("id").item(0).getTextContent());
							if (product.getPicture() != null) {
								this.addImages(product.getPicture());
							}
							
							NodeList nodeList = document.getElementsByTagName("stock_availables").item(0).getChildNodes();
							String stock_id = null;
							for (int i = 0; i < nodeList.getLength(); i++) {
								if (nodeList.item(i).getNodeType() == Node.ELEMENT_NODE) {
									Element element = (Element) nodeList.item(i);
										stock_id = element.getElementsByTagName("id").item(0).getTextContent().toString();
								}
							}
							
							List<StockMoveLine> moveLine = Beans.get(StockMoveLineRepository.class).all().filter("self.stockMove.statusSelect = 3 and (self.stockMove.fromLocation.typeSelect = 1 or self.stockMove.toLocation.typeSelect = 1) and self.product = ?", product).fetch();
							BigDecimal totalRealQty = BigDecimal.ZERO;
							
							if(!moveLine.isEmpty()) {
								
								for(int i=0 ; i<moveLine.size(); i++) {
									totalRealQty =  totalRealQty.add(moveLine.get(i).getRealQty());
								}
								this.addStock(stock_id, totalRealQty.setScale(0, RoundingMode.HALF_UP).toString());
							}
							
							productRepo.save(product);
							
						} else {
							this.updatRecord("products", schema, product.getPrestaShopId());
						}

					} else {
						throw new AxelorException(String.format("Product Name is null or invalid"), IException.NO_VALUE);
					}
				} catch (AxelorException e) {
					this.exportLog(product.getId().toString(), e.getMessage());
					continue;
				} catch (Exception e) {
					this.exportLog(product.getId().toString(), e.getMessage());
					continue	;
				}
			}
	}
	
	@SuppressWarnings("deprecation")
	@Transactional
	public void exportAxelorSaleOrders(ZonedDateTime endDate) throws PrestaShopWebserviceException, TransformerException, IOException {
		
		String cart_id = "";
		this.exportLogObjectHeder("SaleOrder");
		List<SaleOrder> orders = null;
		
		if(endDate == null) {
			if(isStatus == true) {
				orders = Beans.get(SaleOrderRepository.class).all().fetch();
			} else {
				orders = Beans.get(SaleOrderRepository.class).all().filter("self.statusSelect = 1").fetch();
			}
		} else {
			if(isStatus == true) {
				orders = Beans.get(SaleOrderRepository.class).all().filter("self.createdOn > ?1 OR self.updatedOn > ?2 OR self.prestaShopId = null", endDate, endDate).fetch();
			} else {
				orders = Beans.get(SaleOrderRepository.class).all().filter("(self.createdOn > ?1 OR self.updatedOn > ?2 OR self.prestaShopId = null) AND self.statusSelect = 1", endDate, endDate).fetch();
			}			
		}
		
		for (SaleOrder saleOrder : orders) {
			
			String saleOrder_product_id[] = new String[saleOrder.getSaleOrderLineList().size()];
			String id_customer = "";
			String id_address_delivery = "";
			String id_address_invoice = "";
			String secure_key = "";
			String cartId = "";
			String id_currency = "";
				if (saleOrder.getPrestaShopId() == null) {
					this.exportNewConnection("carts");
				} else {
					cart_id = this.getCartId(saleOrder);
					this.exportUpdateConnection("carts", cart_id);
				}
				
				try {
					
					if (!saleOrder.getClientPartner().getPrestaShopId().isEmpty()) {
						
						id_customer = saleOrder.getClientPartner().getPrestaShopId();
						id_address_delivery = saleOrder.getDeliveryAddress().getPrestaShopId();
						id_address_invoice =  saleOrder.getMainInvoicingAddress().getPrestaShopId();
						Currency currency = currencyRepo.findByCode(saleOrder.getCurrency().getCode());
						id_currency = currency.getPrestaShopId();
						
						schema.getElementsByTagName("id_shop_group").item(0).setTextContent("1");
						schema.getElementsByTagName("id_shop").item(0).setTextContent("1");
						schema.getElementsByTagName("id_carrier").item(0).setTextContent("1");
						schema.getElementsByTagName("id_currency").item(0).setTextContent(id_currency);
						schema.getElementsByTagName("id_lang").item(0).setTextContent("1");
						
						if(id_address_delivery == null) {
							throw new AxelorException(String.format("Address in Order null or invalid"),IException.NO_VALUE);
						} else {
							schema.getElementsByTagName("id_address_delivery").item(0).setTextContent(id_address_delivery);
						}
						
						schema.getElementsByTagName("id_address_invoice").item(0).setTextContent(id_address_invoice);
						schema.getElementsByTagName("id_customer").item(0).setTextContent(id_customer.toString());
						schema.getElementsByTagName("secure_key").item(0).setTextContent(secure_key.toString());
						
						for(int k = 0; k < saleOrder.getSaleOrderLineList().size(); k++) {
							
							if(saleOrder.getSaleOrderLineList().get(k).getProduct() != null) {
								saleOrder_product_id[k] = saleOrder.getSaleOrderLineList().get(k).getProduct().getPrestaShopId();
							}
						}
						
						this.addCartProduct(saleOrder, saleOrder_product_id, id_address_delivery);
						
						if (saleOrder.getPrestaShopId() == null) {
							Document document = this.addRecord("carts", schema);
							cartId = document.getElementsByTagName("id").item(0).getTextContent();
							
						} else {
							this.updatRecord("carts", schema, cart_id);
						}
									
						this.createOrder(saleOrder, id_address_delivery, id_address_invoice, cartId, cart_id, id_currency, id_customer, saleOrder_product_id, endDate);
					}
					
				} catch (AxelorException e) {
					this.exportLog(saleOrder.getId().toString(), e.getMessage());
					continue;
				} catch (Exception e) {
					this.exportLog(saleOrder.getId().toString(), e.getMessage());
					continue;
				}
			}
	}
	
	@SuppressWarnings("deprecation")
	public void exportAxelorSaleOrderLines() throws PrestaShopWebserviceException, TransformerException, IOException {
			
			this.exportLogObjectHeder("SaleOrderLines");
			List<SaleOrderLine> saleOrderLines = null;
			
			if(isStatus == true) {
				saleOrderLines = Beans.get(SaleOrderLineRepository.class).all().filter("self.saleOrder.prestaShopId != null").fetch();
			} else {
				saleOrderLines = Beans.get(SaleOrderLineRepository.class).all().filter("self.saleOrder.statusSelect = 1 AND self.saleOrder.prestaShopId != null").fetch();
			}
			
			for(SaleOrderLine orderLine : saleOrderLines) {
				this.exportResetOrderDetails(orderLine.getSaleOrder().getPrestaShopId());
			}
			
			for(SaleOrderLine orderLine : saleOrderLines) {
				try {
					
					if(orderLine.getProduct() != null && orderLine.getSaleOrder().getPrestaShopId() != null) {
						
						this.exportNewConnection("order_details");
						
						schema.getElementsByTagName("id_order").item(0).setTextContent(orderLine.getSaleOrder().getPrestaShopId());
						schema.getElementsByTagName("product_id").item(0).setTextContent(orderLine.getProduct().getPrestaShopId());
						schema.getElementsByTagName("product_name").item(0).setTextContent(orderLine.getProduct().getName());
						schema.getElementsByTagName("product_quantity").item(0).setTextContent(orderLine.getQty().setScale(0, RoundingMode.HALF_UP).toString());
						schema.getElementsByTagName("unit_price_tax_incl").item(0).setTextContent(orderLine.getPrice().setScale(2, RoundingMode.HALF_UP).toString());
						schema.getElementsByTagName("unit_price_tax_excl").item(0).setTextContent(orderLine.getPrice().setScale(2, RoundingMode.HALF_UP).toString());
						schema.getElementsByTagName("product_price").item(0).setTextContent(orderLine.getPrice().setScale(2, RoundingMode.HALF_UP).toString());
						schema.getElementsByTagName("id_warehouse").item(0).setTextContent("0");
						schema.getElementsByTagName("id_shop").item(0).setTextContent("1");
						
						Document document = this.addRecord("order_details", schema);
						orderLine.setPrestaShopId(document.getElementsByTagName("id").item(0).getTextContent());
						saleOrderLineRepo.save(orderLine);

					} else {
						throw new AxelorException(String.format("This order is not on prestashop or invalid product"),IException.NO_VALUE);
					}
				} catch (AxelorException e) {
					this.exportLog(orderLine.getId().toString(), e.getMessage());
					continue;
				} catch (Exception e) {
					this.exportLog(orderLine.getId().toString(), e.getMessage());
					continue;
				}
			}
		}
}