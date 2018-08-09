/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package session;

import java.util.HashMap;
import java.util.Map;
import mysql.SessionDTO;

/**
 *
 * @author root
 */
public class SessionStorage {
    private static Map<String, SessionDTO> sessions = new HashMap<String, SessionDTO>();
    
    public SessionDTO getSession(String sessionId, String tabId){
        return sessions.get(sessionId+"-"+tabId);
    }
    
    public void addSession(String sessionId, String tabId){
        
    }
}
