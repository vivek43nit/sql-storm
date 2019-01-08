/*
 * The MIT License
 *
 * Copyright 2018 Vivek Kumar <vivek43nit@gmail.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.vivek.sqlstorm.dto;

import com.vivek.sqlstorm.datahandler.DataManager;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Vivek Kumar <vivek43nit@gmail.com>
 */
public class ColumnDTO {
    private String name;
    private String description;
    private int dataType;
    private int size;
    private int nullable;
    
    //indexing info
    private boolean indexed;
    private boolean primaryKey;
    private boolean unique;

    private List<ColumnPath> referTo;
    private List<ColumnPath> referencedBy;

    public ColumnDTO(String name) {
        this.name = name;
        this.referTo = new ArrayList<ColumnPath>();
        this.referencedBy = new ArrayList<ColumnPath>();
    }
    
    public ColumnDTO(String name, String description, int dataType, int size, int nullable) {
        this.name = name;
        this.description = description;
        this.dataType = dataType;
        this.size = size;
        this.nullable = nullable;
        
        this.referTo = new ArrayList<ColumnPath>();
        this.referencedBy = new ArrayList<ColumnPath>();
    }

    public boolean isIndexed() {
        return indexed;
    }

    public void setIndexed(boolean indexed) {
        this.indexed = indexed;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    
    public List<ColumnPath> getReferTo() {
        return referTo;
    }

    public void addReferTo(ColumnPath referTo) {
        this.referTo.add(referTo);
    }

    public List<ColumnPath> getReferencedBy() {
        return referencedBy;
    }

    public void addReferencedBy(ColumnPath referencedBy) {
        this.referencedBy.add(referencedBy);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDataType() {
        return dataType;
    }

    public void setDataType(int dataType) {
        this.dataType = dataType;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getNullable() {
        return nullable;
    }

    public void setNullable(int nullable) {
        this.nullable = nullable;
    }
    
    public String getFinalValueFromDb(String data){
        //conversion yet... but may be supported in future
        return DataManager.get(null, data);
    }
}
