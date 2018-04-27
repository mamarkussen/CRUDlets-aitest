/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tna.endpoints;

import com.tna.common.AccessError;
import org.json.simple.JSONObject;

/**
 *
 * @author tareq
 */
public abstract class AuthenticationEndpoint extends BasicEndpoint {

    @Override
    public JSONObject doList() {
        return null;
    }

    @Override
    public JSONObject doCreate(JSONObject json) {
        try {
            return login(json);
        } catch (AccessError e) {
            return null;
        }
    }

    @Override
    public JSONObject doUpdate(JSONObject obj, int resource) {
        return null;
    }

    @Override
    public JSONObject doRead(int resource) {
        return null;
    }

    @Override
    public JSONObject doDelete(int resource) {
        return null;
    }

    /**
     * Login a user to the service.
     * @param credentials The credentials of the user who wants to login.
     * @return a session token.
     * @throws AccessError on failure.
     */
    public abstract JSONObject login(JSONObject credentials) throws AccessError;

}
