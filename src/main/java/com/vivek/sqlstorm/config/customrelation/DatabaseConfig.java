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
package com.vivek.sqlstorm.config.customrelation;

import java.util.List;
import java.util.Map;
import com.vivek.sqlstorm.dto.ReferenceDTO;
import com.vivek.sqlstorm.dto.MappingTableDto;

/**
 *
 * @author Vivek Kumar <vivek43nit@gmail.com>
 */
public class DatabaseConfig {
    private List<ReferenceDTO> relations;
    private Map<String, MappingTableDto> jointTables;
    private Map<String, List<String>> autoResolve;
    
    public List<ReferenceDTO> getRelations() {
        return relations;
    }

    public void setRelations(List<ReferenceDTO> relations) {
        this.relations = relations;
    }

    public Map<String, MappingTableDto> getJointTables() {
        return jointTables;
    }

    public void setJointTables(Map<String, MappingTableDto> jointTables) {
        this.jointTables = jointTables;
    }

    public Map<String, List<String>> getAutoResolve() {
        return autoResolve;
    }

    public void setAutoResolve(Map<String, List<String>> autoResolve) {
        this.autoResolve = autoResolve;
    }

    @Override
    public String toString() {
        return "DatabaseConfig{" + "relations=" + relations + ", jointTables=" + jointTables + ", autoResolve=" + autoResolve + '}';
    }
}
