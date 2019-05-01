/**
 * Copyright © 2018-2019 Hashmap, Inc
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
package com.hashmapinc.tempus.witsml.valve.dot.model.log;

import com.hashmapinc.tempus.witsml.valve.dot.JsonUtil;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.util.Iterator;

public class LogConverterExtended
        extends com.hashmapinc.tempus.WitsmlObjects.Util.LogConverter {

    protected static JSONObject objLogJO;
    protected static JSONObject workObj;

    /**
     * convertToChannelSet1311 takes in a v1.3.1.1 JSON string
     *          that was produced client-side from SOAP WITSML XML
     *          & translates as necessary to adhere to DoT's
     *          "Create a new ChannelSet" API.
     * @param jsonString Represents the client's WITSML object (complete)
     *                   It is a JSON String created from the ObjLog1311
     * @return JSON String representing the conversion
     */
    public static String convertToChannelSet1311( String jsonString ) {
        // return the payload for creating a 1.3.1.1 ChannelSet
        return null;
    }

    /**
     * convertToChannelSet1411 takes in a v1.4.1.1 WITSML object
     *          that was produced client-side from SOAP WITSML XML
     *          & translates as necessary to adhere to DoT's
     *          "Create a new ChannelSet" &
     *          "Post channels to existing ChannelSet" API.
     *
     * Conversion at this stage exists to handle data business rules
     * as documented by the DoT API.
     *
     * @param witsmlObj   Represents the client's WITSML object v1.4.1.1.
     *                    This object has been marshaled by JAXB
     *                         from the faw XML sent by the client.
     * @return JSONObject Represents the conversion of both the
     *                         channel set and the channels.
     */
    public static JSONObject convertToChannelSet1411(
            com.hashmapinc.tempus.WitsmlObjects.v1411.ObjLog witsmlObj ) {

        objLogJO = new JSONObject(
                witsmlObj.getJSONString("1.4.1.1") );

        // groom the data
        removeNullsFrom(objLogJO);
        JsonUtil.removeEmpties(objLogJO);

        // transform names per DoT API/mapping documentation
        // *********
        renameString("pass",
                "passNumber",
                objLogJO);
        renameString("indexType",
                "timeDepth",
                objLogJO);
        renameString("serviceCompany",
                "loggingCompanyName",
                objLogJO);

        // ************** citation created JSON object ****************
        createJO();

        // ********************* hardcoded logParam *********************
        // Biz Rule: When logParam is present, the "index" field is required;
        //           otherwise, API fails
        // TODO Still debugging why logParam comes in garbled with tab + 2 newlines
        //           TEMPORARY WORKAROUND
        if (objLogJO.has("logParam")) {
            objLogJO.remove("logParam");
        }

        // =============================== channels ===============================
        if ( objLogJO.has("logCurveInfo")
                && objLogJO.getJSONArray("logCurveInfo").length() > 0 ) {

            // move all "logCurveInfo" into a JSON array for async processing
            JSONArray workArray = objLogJO.getJSONArray("logCurveInfo");
            objLogJO.remove("logCurveInfo");

            AsyncConvertToChannel asyncConvertToChannel = new AsyncConvertToChannel();
            for (int i=0; i<workArray.length(); ++i) {
                JsonUtil.removeEmpties(workArray.getJSONObject(i));
                asyncConvertToChannel.convertToChannel1411(workArray.getJSONObject(i), objLogJO);
            }
            // put the converted logCurveInfo back
            objLogJO.put("logCurveInfo", workArray);
        } // End of logCurveInfo

        // Now the JSON should be mapped according to DoT's API expectations
        // Those expectations match the POJOs generated from DoT's API
        // TODO Create the POJOs using Jackson

        // return the payloads for creating a 1.4.1.1 ChannelSet & updating the Channel Set
        // with 1.4.1.1 Channel data
        return objLogJO;
    }

    /**
     * createJO converts the following JSONObject from the old name
     *          to the new name, and puts them (one or both) into
     *          a JSONObject
     */
    protected static void createJO() {

        boolean createdAnObject = false;
        if (objLogJO.has("name")) {
            workObj = new JSONObject();
            createdAnObject = true;
            workObj.put("title", objLogJO.getString("name"));
            objLogJO.remove("name");
        }
        if (objLogJO.has("description")) {
            if (!createdAnObject) {
                workObj = new JSONObject();
                createdAnObject = true;
            }
            workObj.put("description", objLogJO.getString("description"));
            objLogJO.remove("description");
        }
        if ( createdAnObject ) {
            objLogJO.put("citation", workObj);
        }
    }

    /**
     * convertTo1411 takes in a JSONObject that represents a ChannelSet &
     *          Channels returned by DoT & translates as necessary to
     *          adhere to the standard for a v1.4.1.1 WITSML object.
     *
     * Conversion at this stage exists to handle the WITSML standard.
     *
     * @param jsonRespChannels Channels returned by DoT
     *
     * @return witsmlObj Represents the client's WITSML object v1.4.1.1.
     *                     This object has been marshalled by JAXB from the raw XML
     *      *                          sent by the client.
     */
    public String convertTo1411(JSONArray jsonResponseCS, JSONArray jsonRespChannels)
                                                throws JAXBException {

        // create JaxBContext to unmarshal channel set JSON into the channel set POJO
        JSONObject joCS = jsonResponseCS.getJSONObject(0); // 1 channel set per log
        JAXBContext jcCS = JAXBContextFactory.createContext(
                new Class[] {com.hashmapinc.tempus.witsml.valve.dot.model.log.channelset.View.class},
                null);
        // create the Unmarshaller Object for channel set using the JaxB Context
        Unmarshaller unmarshallerCS = jcCS.createUnmarshaller();
        unmarshallerCS.setProperty(UnmarshallerProperties.MEDIA_TYPE,
                "application/json");
        unmarshallerCS
                .setProperty(UnmarshallerProperties.JSON_INCLUDE_ROOT, false);
        // create the StreamSource from the channel set JSON response Object...
        StreamSource streamSrcCS = new StreamSource(
                new StringReader(joCS.toString()));
        // create the channel set View from the JSON response Object for
        // channel set
        com.hashmapinc.tempus.witsml.valve.dot.model.log.channelset.View viewCS =
                unmarshallerCS
                        .unmarshal(streamSrcCS, com.hashmapinc.tempus.witsml.valve.dot.model.log.channelset.View.class)
                        .getValue();
        // Print some channel set data to console
        System.out.println("ChannelSet View timeDepth: " + viewCS.getTimeDepth());
        System.out.println("ChannelSet View objectGrowing : " + viewCS.getObjectGrowing());

        // create JaxBContext to unmarshal channel JSON into the channel POJO
        // TODO Do ALL channels next
        JSONObject joCH = jsonRespChannels.getJSONObject(0);
        JAXBContext jcCH = JAXBContextFactory.createContext(
                new Class[] {com.hashmapinc.tempus.witsml.valve.dot.model.log.channel.View.class},
                null);
        // create the Unmarshaller Object for channels using the JaxB Context
        Unmarshaller unmarshallerCH = jcCH.createUnmarshaller();
        // set the Unmarshaller Objects' media type to JSON
        unmarshallerCH.setProperty(UnmarshallerProperties.MEDIA_TYPE,
                "application/json");
        unmarshallerCH
                .setProperty(UnmarshallerProperties.JSON_INCLUDE_ROOT, false);
        // create the StreamSource from the channel JSON response Objects...
        StreamSource streamSrcCH = new StreamSource(
                new StringReader(joCH.toString()));
        // create the channel View from the JSON response Object for a
        // channel
        com.hashmapinc.tempus.witsml.valve.dot.model.log.channel.View viewCH =
                unmarshallerCH
                        .unmarshal(streamSrcCH, com.hashmapinc.tempus.witsml.valve.dot.model.log.channel.View.class)
                        .getValue();
        // Print some channel data to console
        System.out.println("ChannelSet View timeDepth: " + viewCH.getGrowingStatus());
        System.out.println("ChannelSet View objectGrowing : " + viewCH.getTimeDepth());
        // construct the String return value
        JSONObject returnObj = new JSONObject();
        // If I use an array, i can move the whole view at once
        // Rename transformation
        returnObj.put("indexType",viewCS.getTimeDepth());
        returnObj.put("pass",viewCS.getPassNumber());
        returnObj.put("serviceCompany",viewCS.getLoggingCompanyName());
        // TODO Why are nulls not getting removed?
        // groom the response
        removeNullsFrom(returnObj);
        JsonUtil.removeEmpties(returnObj);
        return returnObj.toString();
    }


    // TODO Move to the correct location
    // ************** Utility Methods ****************
    /**
     * renameString
     *
     * @param oldName
     *        newName
     *        objForRename
     */
    protected static void renameString(String oldName,
                                       String newName,
                                       JSONObject objForRename) {
        if (objForRename.has(oldName)) {
            String passValue = objForRename.getString(oldName);
            objForRename.remove(oldName);
            objForRename.put(newName, passValue);
        }
    }
    /**
     * removeNullsFrom
     *
     * @param object
     */
    public static void removeNullsFrom(@Nullable JSONObject object)
    {
        if (object != null) {
            Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                Object o = object.get(key);
                if (o == null || o == JSONObject.NULL) {
                    iterator.remove();
                } else {
                    removeNullsFrom(o);
                }
            }
        }
    }

    /**
     * removeNullsFrom
     *
     * @param array
     */
    public static void removeNullsFrom(@Nullable JSONArray array)
    {
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                Object o = array.get(i);
                if (o == null || o == JSONObject.NULL) {
                    array.remove(i);
                } else {
                    removeNullsFrom(o);
                }
            }
        }
    }

    /**
     * removeNullsFrom
     *
     * @param o
     */
    public static void removeNullsFrom(@NonNull Object o)
    {
        if (o instanceof JSONObject) {
            removeNullsFrom((JSONObject) o);
        } else if (o instanceof JSONArray) {
            removeNullsFrom((JSONArray) o);
        }
    }

}