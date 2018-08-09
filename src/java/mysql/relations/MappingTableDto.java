/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mysql.relations;

/**
 *
 * @author root
 */
public class MappingTableDto {
    public static enum MappingType{
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_MANY
    }
    private MappingType type;
    private String from;
    private String to;

    public MappingType getType() {
        return type;
    }

    public void setType(MappingType type) {
        this.type = type;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    @Override
    public String toString() {
        return type+":"+from + "->" + to;
    }
    
}
