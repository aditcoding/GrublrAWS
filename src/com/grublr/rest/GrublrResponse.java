package com.grublr.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.grublr.util.Utils;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by adi on 9/7/15.
 */
@XmlRootElement
public class GrublrResponse {

    private static final Logger log = Logger.getLogger(GrublrResponse.class.getName());

    private String unique_name;
    private String name;
    private String desc;
    private String lat;
    private String lon;
    private String imageUrl;

    public GrublrResponse() {

    }

    public String getUnique_name() {
        return unique_name;
    }

    public void setUnique_name(String unique_name) {
        this.unique_name = unique_name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getLat() {
        return lat;
    }

    public void setLat(String lat) {
        this.lat = lat;
    }

    public String getLon() {
        return lon;
    }

    public void setLon(String lon) {
        this.lon = lon;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    @Override
    public String toString() {
        try {
            return Utils.objToString(this);
        } catch (JsonProcessingException e) {
            log.log(Level.SEVERE, e.getMessage(), e);
        }
        return "Exception occurred in toString()";
    }

}
