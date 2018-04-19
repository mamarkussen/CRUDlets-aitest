/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tna.data;

import com.tna.common.AccessError;
import com.tna.common.AccessError.ERROR_TYPE;
import com.tna.common.UserAccessControl;
import com.tna.utils.JSON;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.json.simple.JSONObject;

/**
 *
 * @author tareq
 *
 */
public class Persistence {

    public static final String CREATE_OBJECT_SQL = "INSERT INTO %s (%s) VALUES (%s) ";
    public static final String READ_OBJECT_SQL = "SELECT * FROM %s WHERE id = ?";
    public static final String UPDATE_OBJECT_SQL = "UPDATE %s SET %s where id = ?";
    public static final String DELETE_OBJECT_SQL = "DELETE FROM %s WHERE id = ?";
    public static final String LIST_OBJECT_SQL = "SELECT * FROM %s";

    public static final String GET_PRIVILEGE_AND_ID_SQL = "SELECT id,level FROM %s WHERE token = ? ";
    public static final String GET_PASSWORD_SQL = "SELECT password,id FROM %s WHERE userName = ? ";
    public static final String SET_TOKEN_SQL = "UPDATE %s set token = ? where id = ?";

    public static final String READ_OBJECT_USER_SQL = "SELECT user FROM %s WHERE id = ?";
    public static final String LIST_USER_OBJECTS_SQL = "SELECT * FROM %s WHERE user = (SELECT id FROM %s WHERE token = ? )";
    public static final String READ_USER_OBJECT_SQL = "SELECT * FROM %s WHERE id = ? AND user = (SELECT id FROM %s WHERE token = ? ) ";
    public static final String ASSIGN_OBJECT_TO_USER_SQL = "UPDATE %s set user = ? WHERE id = ?; ";
    public static final String UPDATE_USER_OBJECT_SQL = "UPDATE %s SET %s where id = ? AND user = (SELECT id FROM %s WHERE token = ? )";
    public static final String DELETE_USER_OBJECT_SQL = "DELETE FROM %s WHERE id = ? and user = (SELECT id FROM %s WHERE token = ? )";

    public static final String SEARCH_BY_PROPERTY_SQL = "SELECT * from %s WHERE %s";
    public static final String SELECT_OBJECT_USER = "SELECT user from %s where id = ?";
    

    /**
     * Creates a new object and ties it to the user. Returns a success message
     * if it works, returns null otherwise.
     *
     * @param object
     * @param author
     * @param json
     * @param token
     * @return Returns a JSONObject with a success message if the object was
     * successfully created, returns null otherwise.
     * @throws com.tna.common.AccessError
     */
    public static JSONObject create(Class object, Class author, JSONObject json, String token) throws AccessError {
        JSONObject result;
        Connection conn = Access.pool.checkOut();
        try {
            String authorName = author.getSimpleName();
            String className = object.getSimpleName();

            try (PreparedStatement pstmt2 = conn.prepareStatement(String.format(GET_PRIVILEGE_AND_ID_SQL, authorName))) {
                pstmt2.setObject(1, token);
                JSONObject key;

                try (ResultSet rs = pstmt2.executeQuery()) {
                    if (!rs.next()) {
                        throw new AccessError(ERROR_TYPE.USER_NOT_AUTHENTICATED);
                    }
                    long user = rs.getLong("id");
                    key = Persistence.create(object, json);

                    try (PreparedStatement pstmt = conn.prepareStatement(String.format(ASSIGN_OBJECT_TO_USER_SQL, className))) {
                        pstmt.setObject(1, user);
                        pstmt.setObject(2, key.get("key"));
                        if (pstmt.executeUpdate() == 0) {
                            throw new AccessError(ERROR_TYPE.ENTITY_NOT_FOUND);
                        }
                    }
                }
                result = key;
            }
        } catch (SQLException e) {
            System.out.println(e);
            throw new AccessError(ERROR_TYPE.OPERATION_FAILED);
        } finally {
            Access.pool.checkIn(conn);
        }
        return result;
    }

    /**
     * Reads a specific object belonging to the user. If it does not belong to
     * him, it returns nothing.
     *
     * @param object
     * @param author
     * @param json
     * @param resource
     * @return returns a JSONObject of the object called if it belongs to the
     * user, returns null otherwise.
     */
    public static JSONObject read(Class object, Class author, long resource, String token) throws AccessError {
        Connection conn = Access.pool.checkOut();
        try {
            String authorName = author.getSimpleName();
            String className = object.getSimpleName();
            UserAccessControl.authAccess(object, author, token, resource);
            JSONObject result = Persistence.read(object, resource);
            return result;
        } finally {
            Access.pool.checkIn(conn);
        }

    }

    /**
     * Updates an object belonging to the user. If it does not belong to the
     * user, it returns null.
     *
     * @param object
     * @param author
     * @param json
     * @param resource
     * @return returns a JSONObject with the updated object if it belongs to the
     * user, returns null otherwise.
     */
    public static JSONObject update(Class object, Class author, JSONObject json, long resource, String token) throws AccessError {
        Connection conn = Access.pool.checkOut();
        try {
            String authorName = author.getSimpleName();
            String className = object.getSimpleName();
            UserAccessControl.authAccess(object, author, token, resource);
            JSONObject result = Persistence.update(object, resource, json);
            return result;
        } finally {
            Access.pool.checkIn(conn);
        }

    }

    /**
     * Deletes an object belonging to the user. Returns a success message if it
     * does, returns null otherwise.
     *
     * @param object
     * @param author
     * @param json
     * @param resource
     * @return returns a JSONObject with a success message if the object belongs
     * to the user, returns null otherwise.
     */
    public static JSONObject delete(Class object, Class author, long resource, String token) throws AccessError {
        Connection conn = Access.pool.checkOut();
        try {
            String authorName = author.getSimpleName();
            String className = object.getSimpleName();
            UserAccessControl.authAccess(object, author, token, resource);
            JSONObject result = Persistence.delete(object, resource);
            return result;
        } finally {
            Access.pool.checkIn(conn);
        }
    }

    /**
     * Lists all the objects belonging to the user.
     *
     * @param object
     * @param author
     * @param json
     * @return returns a JSONObject of the user's associated object of this type
     */
    public static JSONObject list(Class object, Class author, String token) throws AccessError {
        JSONObject result = new JSONObject();
        Connection conn = Access.pool.checkOut();

        try {
            String className = object.getSimpleName();
            String authorName = author.getSimpleName();
            UserAccessControl.checkToken(author, token);
            try (PreparedStatement pstmt = conn.prepareStatement((String.format(LIST_USER_OBJECTS_SQL, className, authorName)), Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setObject(1, token);
                try(ResultSet rs = pstmt.executeQuery()){
                Field[] fields = getAllFields(object);
                int i = 0;
                if (rs.next() == false) {
                    throw new AccessError(ERROR_TYPE.ENTITY_NOT_FOUND);
                    
                }
                do {
                    JSONObject temp = new JSONObject();
                    for (Field field : fields) {
                        try {
                            temp.put(field.getName(), rs.getObject(field.getName()));
                        } catch (IllegalArgumentException ex) {
                        }
                    }
                    result.put(i, temp);
                    i++;
                } while (rs.next());
            }}
        } catch (SQLException e) {
            System.out.println(e);
            throw new AccessError(ERROR_TYPE.OPERATION_FAILED);
        } finally {
            Access.pool.checkIn(conn);
        }
        return result;
    }

    /**
     * Searchers for an object by a set of properties and values. Returns the
     * first one.
     *
     * @param object
     * @param json
     * @return returns a JSONObject of the first object fulfilling this
     * criteria.
     */
    public static JSONObject readByProperties(Class object, JSONObject json) {
        JSONObject result = new JSONObject();
        Connection conn = Access.pool.checkOut();

        try {
            StringBuilder valuesString = new StringBuilder();
            Set keys = json.keySet();
            int i = 0;
            int length = keys.size();
            for (Object key : keys) {
                try {
                    object.getDeclaredField(key.toString());
                    valuesString.append(key.toString());
                    valuesString.append(" = ?");
                    if (i < length - 1) {
                        valuesString.append(" and ");
                    }
                    i++;
                } catch (NoSuchFieldException | SecurityException ex) {
                    throw new SQLException();
                }
            }
            String className = object.getSimpleName();
            PreparedStatement pstmt = conn.prepareStatement((String.format(SEARCH_BY_PROPERTY_SQL, className, valuesString.toString())), Statement.RETURN_GENERATED_KEYS);
            i = 1;
            for (Object key : keys) {
                pstmt.setObject(i, json.get(key));
                i++;
            }
            ResultSet rs = pstmt.executeQuery();
            Field[] fields = getAllFields(object);
            rs.next();
            for (Field field : fields) {
                try {
                    result.put(field.getName(), rs.getObject(field.getName()));
                } catch (IllegalArgumentException ex) {

                }
            }
            rs.close();
            pstmt.close();
        } catch (SQLException e) {
            System.out.println(e);
            result = null;
        } finally {
            Access.pool.checkIn(conn);
        }
        return result;
    }

    /**
     * Searchers for objects by a set of properties and values. Returns a list
     * of them all.
     *
     * @param object
     * @param json
     * @return returns a JSONObject of all the objects fulfilling this criteria.
     */
    public static JSONObject listByProperties(Class object, JSONObject json) {
        JSONObject result = new JSONObject();
        Connection conn = Access.pool.checkOut();

        try {
            StringBuilder valuesString = new StringBuilder();
            Set keys = json.keySet();
            int i = 0;
            int length = keys.size();
            for (Object key : keys) {
                try {
                    object.getDeclaredField(key.toString());
                    valuesString.append(key.toString());
                    valuesString.append(" = ?");
                    if (i < length - 1) {
                        valuesString.append(" and ");
                    }
                    i++;
                } catch (NoSuchFieldException | SecurityException ex) {
                    throw new SQLException();
                }
            }
            String className = object.getSimpleName();
            PreparedStatement pstmt = conn.prepareStatement((String.format(SEARCH_BY_PROPERTY_SQL, className, valuesString.toString())), Statement.RETURN_GENERATED_KEYS);
            i = 1;
            for (Object key : keys) {
                pstmt.setObject(i, json.get(key));
                i++;
            }
            ResultSet rs = pstmt.executeQuery();
            Field[] fields = getAllFields(object);
            while (rs.next()) {
                JSONObject obj = new JSONObject();
                for (Field field : fields) {
                    try {
                        obj.put(field.getName(), rs.getObject(field.getName()));
                    } catch (IllegalArgumentException ex) {
                    }
                }
                result.put(rs.getObject("id"), obj);
            }
            rs.close();
            pstmt.close();
        } catch (SQLException e) {
            System.out.println(e);
            result = null;
        } finally {
            Access.pool.checkIn(conn);
        }
        return result;

    }

    /**
     * Creates a new object.
     *
     * @param object
     * @param json
     * @return returns a success code if creation was successful, returns null
     * otherwise.
     */
    public static JSONObject create(Class object, JSONObject json) throws AccessError {
        Connection conn = Access.pool.checkOut();

        JSONObject result = JSON.successResponse();
        try {
            String className = object.getSimpleName();
            Field[] fields = object.getDeclaredFields();
            StringBuilder columns = new StringBuilder();
            StringBuilder values = new StringBuilder();
            for (Field field : fields) {
                columns.append(field.getName());
                values.append("?,");
                columns.append(",");
            }
            columns.deleteCharAt((columns.length() - 1));
            values.deleteCharAt((values.length() - 1));
            try(PreparedStatement pstmt = conn.prepareStatement((String.format(CREATE_OBJECT_SQL, className, columns.toString(), values.toString())), Statement.RETURN_GENERATED_KEYS)){
            int i = 1;
            for (Field field : fields) {
                try {
                    pstmt.setObject(i, json.get(field.getName()));
                } catch (IllegalArgumentException ex) {
                }
                i++;
            }
            pstmt.execute();
            try(ResultSet rs = pstmt.getGeneratedKeys()){
            if(!rs.next()){
                throw new AccessError(ERROR_TYPE.ENTITY_NOT_FOUND);
            }
            result.put("key", rs.getLong(1));
            }
            }
        } catch (SQLException e) {
            System.out.println(e);
            throw new AccessError(ERROR_TYPE.OPERATION_FAILED);
        } finally {
            Access.pool.checkIn(conn);
        }
        return result;
    }

    /**
     * Reads an object.
     *
     * @param object
     * @param id
     * @return returns a JSONObject if the object exists, returns null
     * otherwise.
     */
    public static JSONObject read(Class object, long id) throws AccessError {
        JSONObject result = new JSONObject();
        Connection conn = Access.pool.checkOut();
        try {
            String className = object.getSimpleName();
            try(PreparedStatement pstmt = conn.prepareStatement((String.format(READ_OBJECT_SQL, className)), Statement.RETURN_GENERATED_KEYS)){
            Field[] fields = getAllFields(object);
            pstmt.setLong(1, id);
            try(ResultSet rs = pstmt.executeQuery()){
            if(!rs.next()){
                throw new AccessError(ERROR_TYPE.ENTITY_NOT_FOUND);
            }
            for (Field field : fields) {
                try {
                    result.put(field.getName(), rs.getObject(field.getName()));
                } catch (IllegalArgumentException ex) {
                }
            }
            }}
        } catch (SQLException e) {
            System.out.println(e);
            throw new AccessError(ERROR_TYPE.OPERATION_FAILED);
        } finally {
            Access.pool.checkIn(conn);
        }
        return result;
    }

    /**
     * Updates an existing object.
     *
     * @param object
     * @param id
     * @param json
     * @return returns a JSONObject with a success message if the object was
     * updated, returns null otherwise.
     */
    public static JSONObject update(Class object, long id, JSONObject json) throws AccessError {
        Connection conn = Access.pool.checkOut();
        JSONObject result = null;
        try {
            String className = object.getSimpleName();
            Field[] fields = object.getDeclaredFields();
            StringBuilder values = new StringBuilder();
            Set keySet = json.keySet();
            for (Object key : keySet) {
                try {
                    object.getDeclaredField(key.toString());
                    values.append(key.toString()).append(" = ?,");
                } catch (NoSuchFieldException | SecurityException ex) {
                    throw new AccessError(ERROR_TYPE.OPERATION_FAILED);
                }
            }
            values.deleteCharAt((values.length() - 1));
            try(PreparedStatement pstmt = conn.prepareStatement((String.format(UPDATE_OBJECT_SQL, className, values.toString())), Statement.RETURN_GENERATED_KEYS)){
            int i = 1;
            for (Object key : keySet) {
                try {
                    pstmt.setObject(i, json.get(key));
                } catch (IllegalArgumentException ex) {
                }
                i++;
            }
            pstmt.setObject(i, id);
            int effected = pstmt.executeUpdate();
            if (effected == 0) {
                result = null;
            } else {
                result = JSON.successResponse();
            }
        } }catch (SQLException e) {
            System.out.println(e);
            throw new AccessError(ERROR_TYPE.OPERATION_FAILED);
        } finally {
            Access.pool.checkIn(conn);
        }
        return result;
    }

    /**
     * Deletes an object.
     *
     * @param object
     * @param id
     * @return returns a JSONObject with a success message if deletion worked,
     * returns null otherwise.
     */
    public static JSONObject delete(Class object, long id) throws AccessError {
        JSONObject result = JSON.successResponse();
        Connection conn = Access.pool.checkOut();

        try {
            String className = object.getSimpleName();
            try(PreparedStatement pstmt = conn.prepareStatement((String.format(DELETE_OBJECT_SQL, className)), Statement.RETURN_GENERATED_KEYS)){
            pstmt.setLong(1, id);
            int effected = pstmt.executeUpdate();
            pstmt.close();
            if (effected == 0) {
                throw new AccessError(ERROR_TYPE.ENTITY_NOT_FOUND);
            }
        } }catch (SQLException e) {
            System.out.println(e);
            result = null;
        } finally {
            Access.pool.checkIn(conn);
        }
        return result;
    }

    /**
     * Lists all objects
     *
     * @param object
     * @return returns a JSONObject of JSONObjects of objects with their Ids.
     * Returns null if there are no objects.
     */
    public static JSONObject list(Class object) throws AccessError {
        JSONObject result = new JSONObject();
        Connection conn = Access.pool.checkOut();

        try {
            String className = object.getSimpleName();
            try (PreparedStatement pstmt = conn.prepareStatement((String.format(LIST_OBJECT_SQL, className)), Statement.RETURN_GENERATED_KEYS)) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    Field[] fields = getAllFields(object);
                    int i = 0;
                    if (rs.next() == false) {
                        throw new AccessError(ERROR_TYPE.ENTITY_NOT_FOUND);
                    }
                    do {
                        JSONObject obj = new JSONObject();
                        for (Field field : fields) {
                            try {
                                obj.put(field.getName(), rs.getObject(field.getName()));
                            } catch (IllegalArgumentException ex) {
                            }
                        }
                        result.put(i, obj);
                        i++;
                    } while (rs.next());

                }
            }
        } catch (SQLException e) {
            System.out.println(e);
            throw new AccessError(ERROR_TYPE.OPERATION_FAILED);
        } finally {
            Access.pool.checkIn(conn);
        }
        return result;
    }

    /**
     * Returns all the field of a class and its superclasses.
     *
     * @param type
     * @return returns an array of Fields.
     */
    public static Field[] getAllFields(Class<?> type) {
        ArrayList<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return (fields.toArray(new Field[fields.size()]));
    }
    
    public static JSONObject getUser(Class author,String token) throws AccessError{
        Connection conn = Access.pool.checkOut();
        JSONObject response = new JSONObject();
        try (PreparedStatement pstmt = conn.prepareStatement(String.format(Persistence.GET_PRIVILEGE_AND_ID_SQL, author.getSimpleName()))){
           
            pstmt.setObject(1,token);
            try(ResultSet rs = pstmt.executeQuery()){
            rs.next();
            response.put("id",rs.getLong("id"));
            response.put("level",rs.getLong("level"));
            }
          } catch (SQLException e) {
            System.out.println(e);
            throw new AccessError(ERROR_TYPE.OPERATION_FAILED);
        } finally {
            Access.pool.checkIn(conn);
        }
        return response;
    }

    private static String buildValues(Class object, JSONObject json) {
        StringBuilder values = new StringBuilder();
        Set keySet = json.keySet();
        for (Object key : keySet) {
            try {
                object.getDeclaredField(key.toString());
                values.append(key.toString()).append(" = ?,");
            } catch (NoSuchFieldException | SecurityException ex) {

            }
        }
        values.deleteCharAt((values.length() - 1));
        return values.toString();
    }

}
