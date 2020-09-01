/*
 * h2gis-geotools is an extension to the geotools library to connect H2GIS a 
 * spatial library that brings spatial support to the H2 Java database. *
 *
 * Copyright (C) 2017 LAB-STICC CNRS UMR 6285
 *
 * h2gis-geotools is free software; 
 * you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation;
 * version 3.0 of the License.
 *
 * h2gis-geotools is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details <http://www.gnu.org/licenses/>.
 *
 *
 * For more information, please consult: <http://www.h2gis.org/>
 * or contact directly: info_at_h2gis.org
 */
package org.h2gis.geotools;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.jdbc.JDBCDataStoreFactory;
import org.junit.After;
import org.junit.Before;
import org.locationtech.jts.io.WKTReader;

/**
 *
 * @author Erwan Bocher
 */
public abstract class H2GISDBTestSetUp {
    
    private static final String DB_NAME = "H2GISDBTest";
    
    public H2GISDataStoreFactory factory;
    private HashMap params;
    public JDBCDataStore ds;
    public WKTReader wKTReader;
    

    @Before
    public void setDatabase() throws Exception {
        factory = new H2GISDataStoreFactory();
        factory.setBaseDirectory(new File(getDataBasePath(DB_NAME)));
        params = new HashMap();
        params.put(JDBCDataStoreFactory.NAMESPACE.key, "http://www.geotools.org/h2gis");
        params.put(JDBCDataStoreFactory.DATABASE.key, "h2gis");
        params.put(JDBCDataStoreFactory.DBTYPE.key, "h2gis");
        params.put(JDBCDataStoreFactory.USER, "h2gis");
        params.put(JDBCDataStoreFactory.PASSWD, "h2gis");
        ds = factory.createDataStore( params );
        wKTReader = new WKTReader();
    }
    
    /**
     * Generate a path for the database
     * @param dbName
     * @return 
     */
    private static String getDataBasePath(String dbName) {
        if (dbName.startsWith("file://")) {
            return new File(URI.create(dbName)).getAbsolutePath();
        } else {
            return new File("target/test-resources/" + dbName).getAbsolutePath();
        }
    }

    @After
    public void tearDownDatabase() throws Exception {
        ds.getDataSource().getConnection().close();
    }   
    
    
    
}
