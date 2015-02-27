/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.qpid.jms.selector.filter;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.xpath.CachedXPathAPI;
import org.apache.xpath.objects.XObject;
import org.w3c.dom.Document;
import org.w3c.dom.traversal.NodeIterator;
import org.xml.sax.InputSource;

public class XalanXPathEvaluator implements XPathExpression.XPathEvaluator {
    public static final String DOCUMENT_BUILDER_FACTORY_FEATURE = "org.apache.activemq.apollo.documentBuilderFactory.feature";
    private final String xpath;

    public XalanXPathEvaluator(String xpath) {
        this.xpath = xpath;
    }

    @Override
    public boolean evaluate(Filterable m) throws FilterException {
        String stringBody = m.getBodyAs(String.class);
        if (stringBody!=null) {
            return evaluate(stringBody);
        }
        return false;
    }

    protected boolean evaluate(String text) {
        return evaluate(new InputSource(new StringReader(text)));
    }

    protected boolean evaluate(InputSource inputSource) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            setupFeatures(factory);
            factory.setNamespaceAware(true);
            factory.setIgnoringElementContentWhitespace(true);
            factory.setIgnoringComments(true);
            DocumentBuilder dbuilder = factory.newDocumentBuilder();
            Document doc = dbuilder.parse(inputSource);

            // An XPath expression could return a true or false value instead of a node.
            // eval() is a better way to determine the boolean value of the exp.
            // For compliance with legacy behavior where selecting an empty node returns true,
            // selectNodeIterator is attempted in case of a failure.

            CachedXPathAPI cachedXPathAPI = new CachedXPathAPI();
            XObject result = cachedXPathAPI.eval(doc, xpath);
            if (result.bool()) {
                return true;
            } else {
                NodeIterator iterator = cachedXPathAPI.selectNodeIterator(doc, xpath);
                return (iterator.nextNode() != null);
            }
        } catch (Throwable e) {
            return false;
        }
    }

    protected void setupFeatures(DocumentBuilderFactory factory) {
        Properties properties = System.getProperties();
        List<String> features = new ArrayList<String>();
        for (Map.Entry<Object, Object> prop : properties.entrySet()) {
            String key = (String) prop.getKey();
            if (key.startsWith(DOCUMENT_BUILDER_FACTORY_FEATURE)) {
                String uri = key.split(DOCUMENT_BUILDER_FACTORY_FEATURE + ":")[1];
                Boolean value = Boolean.valueOf((String)prop.getValue());
                try {
                    factory.setFeature(uri, value);
                    features.add("feature " + uri + " value " + value);
                } catch (ParserConfigurationException e) {
                    throw new RuntimeException("DocumentBuilderFactory doesn't support the feature " + uri + " with value " + value + ", due to " + e);
                }
            }
        }

        if (features.size() > 0) {
            StringBuffer featureString = new StringBuffer();
            // just log the configured feature
            for (String feature : features) {
                if (featureString.length() != 0) {
                    featureString.append(", ");
                }
                featureString.append(feature);
            }
        }
    }
}