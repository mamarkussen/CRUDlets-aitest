package Entities;


import com.tna.Entities.AuthorisationEntity;
import org.json.simple.JSONObject;

public class User extends AuthorisationEntity {
	
	@Override
    public JSONObject list(){
       return Persistence.list(this);
    }

    @Override
    public JSONObject create(JSONObject obj){
       JSON.JSONtoObject(this,obj);
       return Persistence.create(this);
    }

    @Override
    public JSONObject update(JSONObject obj, int resource){
        JSON.JSONtoObject(this,obj);
        return Persistence.update(this,resource);
    }

    @Override
    public JSONObject read(int resource){
         return Persistence.read(this,resource);
    }

    @Override
    public JSONObject delete(int resource){
       return Persistence.delete(this,resource);
    }
    

}
