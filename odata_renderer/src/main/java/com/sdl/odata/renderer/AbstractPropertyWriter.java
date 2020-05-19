/**
 * Copyright (c) 2014 All Rights Reserved by the SDL Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sdl.odata.renderer;

import com.sdl.odata.api.ODataException;
import com.sdl.odata.api.edm.ODataEdmException;
import com.sdl.odata.api.edm.model.EntityDataModel;
import com.sdl.odata.api.edm.model.StructuredType;
import com.sdl.odata.api.edm.model.Type;
import com.sdl.odata.api.parser.ODataUri;
import com.sdl.odata.api.parser.ODataUriUtil;
import com.sdl.odata.api.parser.TargetType;
import com.sdl.odata.api.renderer.ODataRenderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;

import java.util.List;

import static com.sdl.odata.ODataRendererUtils.checkNotNull;
import static com.sdl.odata.util.edm.EntityDataModelUtil.getAndCheckType;

/**
 * Handles property writing.
 *
 */
public abstract class AbstractPropertyWriter {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractPropertyWriter.class);
    private final ODataUri oDataUri;
    private final EntityDataModel entityDataModel;
    private final TargetType targetType;

    public AbstractPropertyWriter(ODataUri oDataUri, EntityDataModel entityDataModel) throws ODataRenderException {
        this.oDataUri = checkNotNull(oDataUri);
        this.entityDataModel = checkNotNull(entityDataModel);
        this.targetType = getTargetType();
    }

    /**
     * This is main method to get property as string.
     *
     * @param data represents simple primitive or complex value or collections of collection of these.
     * @return String that represents simple primitive or complex value
     * or collections of collection of these in the form of xml or json.
     * @throws ODataException if an error occurs.
     */
    public String getPropertyAsString(Object data) throws ODataException {
        LOG.trace("GetPropertyAsString invoked with {}", data);
        if (data != null) {
            return makePropertyString(data);
        } else {
            return generateNullPropertyString();
        }
    }

    /**
     * This abstract method this needs to be implemented in subclass. Purpose of this method is to
     * generate string (either json or xml ) if the given property is null. For example, atom null string
     *
     * {@code
     * <pre>
     *  <metadata:value xmlns:metadata="metadata name space uri" metadata:context="some context" metadata:null="true" />
     * </pre>
     * }
     *
     * @return String that represents null property
     * @throws com.sdl.odata.api.renderer.ODataRenderException in case of any problems
     */
    protected abstract String generateNullPropertyString() throws ODataException;

    /**
     * This method handles simple primitive property and generates string based on property. For example following
     * atom xml generates in case of simple primitive in AtomPropertyWriter.
     *
     * {@code
     * <pre>
     *     <value xmlns="metadata namespace uri" context="context">CEO</value>
     * </pre>
     * }
     *
     * @param data that represents primitive data. This will never be null.
     * @param type type of the property. This will never be null.
     * @return String that represents simple property
     * @throws com.sdl.odata.api.renderer.ODataRenderException in case of any problems
     */
    protected abstract String generatePrimitiveProperty(Object data, Type type) throws ODataException;

    /**
     * This method handles complex properties and generates string based on property. For example following
     * atom xml generates in case of complex property in AtomPropertyWriter.
     *
     * {@code
     * <pre>
     * <metadata:value metadata:type="#Model.Address" metadata:context="context"
     * xmlns:metadata="metadata namespace uri"
     * xmlns="http://docs.oasis-open.org/odata/ns/data">
     * <Street>Obere Str. 57</Street>
     * <City>Berlin</City>
     * <Region metadata:null="true"/>
     * <PostalCode>D-12209</PostalCode>
     * </metadata:value>
     * </pre>
     * }
     *
     * @param data that represents complex property data. This will never be null.
     * @param type is StructuredType. This will never be null.
     * @return String that represents simple property
     * @throws com.sdl.odata.api.renderer.ODataRenderException in case of any problems
     */
    protected abstract String generateComplexProperty(Object data, StructuredType type) throws ODataException;

    private String makePropertyString(Object data) throws ODataException {
        String propertyXML = null;
        Type type = getTypeFromODataUri();
        validateRequest(type, data);
        switch (type.getMetaType()) {
            case PRIMITIVE:
                LOG.trace("Given property type is primitive: " + type.getClass().getCanonicalName());
                propertyXML = generatePrimitiveProperty(data, type);
                break;

            case COMPLEX:
                LOG.trace("Given property type is complex: " + type.getClass().getCanonicalName());
                propertyXML = generateComplexProperty(data, (StructuredType) type);
                break;

            default:
                LOG.trace("Given property type is default: " + type.getClass().getCanonicalName());
                defaultHandling(type);
        }
        return propertyXML;
    }

    private void validateRequest(Type type, Object data) throws ODataRenderException, ODataEdmException {
        if (!areValidTypesToProceed(type, data)) {
            throw new ODataRenderException("ODataUri type is not matched with given 'data' type: " + type);
        }
    }

    private boolean areValidTypesToProceed(Type type, Object data) throws ODataEdmException {
        return isEmptyCollection(data) || !(isCollection(data) ^ targetType.isCollection())
                && getType(data).equals(type);
    }

    protected Type getTypeFromODataUri() throws ODataRenderException {
        return entityDataModel.getType(targetType.typeName());
    }

    private TargetType getTargetType() throws ODataRenderException {
        Option<TargetType> targetTypeOption = ODataUriUtil.resolveTargetType(oDataUri, entityDataModel);
        if (targetTypeOption.isEmpty()) {
            throw new ODataRenderException("Target type should not be empty");
        }
        return targetTypeOption.get();
    }

    protected Type getType(Object data) throws ODataEdmException {
        Type type;
        if (isEmptyCollection(data)) {
            throw new ODataEdmException("Given property is empty collection for " + data);
        }
        if (isCollection(data)) {
            LOG.trace("Given property is collection");
            List<?> dataList = (List<?>) data;
            type = getAndCheckType(entityDataModel, dataList.get(0).getClass());
        } else {
            type = getAndCheckType(entityDataModel, data.getClass());
        }
        return type;
    }

    protected boolean isEmptyCollection(Object data) {
        return isCollection(data) && ((List<?>) data).isEmpty();
    }

    protected boolean isCollection(Object data) {
        return data instanceof List;
    }

    protected ODataUri getODataUri() {
        return oDataUri;
    }

    protected EntityDataModel getEntityDataModel() {
        return entityDataModel;
    }

    protected void defaultHandling(Type type) throws ODataRenderException {
        String msg = String.format("Unhandled object type %s", type);
        LOG.warn(msg);
        throw new ODataRenderException(msg);
    }
}
