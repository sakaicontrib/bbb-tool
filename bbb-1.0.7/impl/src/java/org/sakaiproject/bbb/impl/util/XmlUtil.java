/**
 * Copyright (c) 2010-2009 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sakaiproject.bbb.impl.util;

import java.beans.IntrospectionException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;

import org.apache.commons.betwixt.AttributeDescriptor;
import org.apache.commons.betwixt.expression.Context;
import org.apache.commons.betwixt.io.BeanReader;
import org.apache.commons.betwixt.io.BeanWriter;
import org.apache.commons.betwixt.strategy.DefaultObjectStringConverter;
import org.apache.commons.betwixt.strategy.ValueSuppressionStrategy;
import org.sakaiproject.bbb.api.Props;

/**
 * Utility class for transforming XML to and from a org.sakaiproject.bbb.api.Props java object.
 * @author Nuno Fernandes
 */
public class XmlUtil {

	public static Props convertXmlToProps(String inputString) throws Exception {
		BeanReader beanReader = getBeanReader();	    
	    StringReader reader = null;
	    Props props = null;
	    try{
		    reader = new StringReader(inputString);
		    props = (Props) beanReader.parse(reader);
	    }finally{
	    	if(reader != null) {
	    		reader.close();
	    	}
	    }
	    if(props == null) props = new Props();
	    return props;
	}

	public static String convertPropsToXml(Props props) throws Exception {
		String xml = "";
		StringWriter outputWriter = null;
		try{
			outputWriter = new StringWriter(); 
			outputWriter.write("<?xml version='1.0' ?>"); 
			BeanWriter beanWriter = getBeanWriter(outputWriter);
			beanWriter.write("MeetingProperties", props);
			xml = outputWriter.toString();
		}finally{
			outputWriter.close();
		}
        return xml;
	}

	
	private static BeanWriter getBeanWriter(final StringWriter outputWriter) {
		BeanWriter beanWriter = new BeanWriter(outputWriter);
	    beanWriter.getXMLIntrospector().getConfiguration().setAttributesForPrimitives(false);
	    beanWriter.getBindingConfiguration().setMapIDs(false);
	    beanWriter.getBindingConfiguration().setValueSuppressionStrategy(new NullEmptyValueSuppressionStrategy());
	    beanWriter.getBindingConfiguration().setObjectStringConverter(new PropertiesObjectStringConverter());
	    beanWriter.setEndOfLine("");		
		return beanWriter;
	}
	
	private static BeanReader getBeanReader() throws IntrospectionException {
		BeanReader beanReader = new BeanReader();
        beanReader.getXMLIntrospector().getConfiguration().setAttributesForPrimitives(false);
        beanReader.getBindingConfiguration().setMapIDs(false);
        beanReader.getBindingConfiguration().setValueSuppressionStrategy(new NullEmptyValueSuppressionStrategy());
        beanReader.getBindingConfiguration().setObjectStringConverter(new PropertiesObjectStringConverter());
        beanReader.registerBeanClass("List", ArrayList.class);
	    beanReader.registerBeanClass("MeetingProperties", Props.class);
		return beanReader;
	}
	
	private static class NullEmptyValueSuppressionStrategy extends ValueSuppressionStrategy {

		@Override
		public boolean suppressAttribute(AttributeDescriptor attributeDescriptor, String value) {
			if(value == null || "".equals(value.trim())) {
				return true;
			}
			return false;
		}
		
	}
	
	private static class PropertiesObjectStringConverter extends DefaultObjectStringConverter {
		private static final long	serialVersionUID	= 1L;

		@SuppressWarnings("unchecked")
		@Override
		public Object stringToObject(String value, Class type, String flavour, Context context) {
			if(value != null && !("").equals(value.trim())) {
				return super.stringToObject(value, type, flavour, context);
			}else{
				return null;
			}	
		}
	}
}
