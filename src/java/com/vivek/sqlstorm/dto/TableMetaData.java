/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.vivek.sqlstorm.dto;

/**
 *
 * @author Vivek
 */
public class TableMetaData {
    
//    private static final Logger logger = Logger.getLogger(TableMetaData.class);
//    
//    private static HashMap<String, TableMetaData> cache = new HashMap<String, TableMetaData>();
//    
//    public static TableMetaData getInstance(String groupName, String dbName, String tableName){
//        String key = groupName+"-"+dbName+"-"+tableName;
//        
//        TableMetaData metaData = cache.get(key);
//        if(metaData == null){
//            metaData = new TableMetaData(groupName, dbName, tableName);
//            cache.put(key, metaData);
//        }
//        return metaData;
//    }
//    
//    private String groupName;
//    private String dbName;
//    private String tableName;
//    private MultiMap<String, ReferenceDTO> referedBy,referTo;
//    private ArrayList<String> comments;
//    private MappingTableDto mappingInfo;
//
//    private TableMetaData(String groupName, String dbName, String tableName)
//    {
//        this.groupName = groupName;
//        this.dbName = dbName;
//        this.tableName = tableName;
//        this.referedBy = new MultiMap<String, ReferenceDTO>();
//        this.referTo = new MultiMap<String, ReferenceDTO>();
//        this.comments = new ArrayList<String>();
//        this.comments.add("none");
//        loadColumnDataType();
//        loadReferenceDetails();
//        this.mappingInfo = CustomRelationHandler.getMappingTableInfo(dbName, tableName);
//    }
//    
//    public String getDataType(int columnCount){
//        return comments.get(columnCount);
//    }
//    
//    public String getFinalValueFromDb(int columnCount, String data){
//        String dataType = comments.get(columnCount);
//        return DataManager.get(dataType, data);
//    }
//    
//    public String setFinalValueToDb(int columnCount, String data){
//        String dataType = comments.get(columnCount);
//        return DataManager.set(dataType, data);
//    }
//
//    public MultiMap<String, ReferenceDTO> getReferedBy() {
//        return referedBy;
//    }
//
//    public MultiMap<String, ReferenceDTO> getReferTo() {
//        return referTo;
//    }
//
//    public MappingTableDto getMappingInfo() {
//        return mappingInfo;
//    }
//    
//    private void loadColumnDataType(){
//        Connection con = null;
//        try
//        {
//            con = DatabaseManager.getInstance().getConnection(this.groupName, this.dbName);
//            String sql = "show full columns from "+this.tableName;
//            logger.info(sql);
//
//            PreparedStatement ps = con.prepareStatement(sql);
//            
//            ResultSet structure = ps.executeQuery();
//            while (structure.next())
//            {
//                String comment = structure.getString("Comment");
//                comments.add(comment);
//                logger.info("column : "+structure.getString("Field")+" ; comment : "+comment);
//            }
//            structure.close();
//            ps.close();
//        }
//        catch (Exception ex)
//        {
//            logger.error(ex, ex);
//        }
//    }
//    
//    private void loadReferenceDetails(){
//        try {
//            GetReferenceDAO.get(groupName, dbName, tableName, referedBy, referTo);
//        } catch (Exception ex) {
//            logger.error("Unable to load reference details from db", ex);
//        }
//        CustomRelationHandler.getReferences(dbName, tableName, referedBy, referTo);
//    }
}
