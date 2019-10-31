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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import static junit.framework.TestCase.fail;
import org.apache.commons.dbcp.BasicDataSource;
import org.geotools.data.DataStore;
import org.geotools.data.jdbc.datasource.ManageableDataSource;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.jdbc.JDBCDataStoreFactory;
import org.h2.tools.Server;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;


/**
 * 
 *
 */
public class H2GISDataStoreFactoryTest  {
    H2GISDataStoreFactory factory;
    HashMap params;
    
    @Before
    public void setUp() throws Exception {
        factory = new H2GISDataStoreFactory();
        factory.setBaseDirectory(new File("./target/testH2"));
        params = new HashMap();
        params.put(JDBCDataStoreFactory.NAMESPACE.key, "http://www.geotools.org/test");
        params.put(JDBCDataStoreFactory.DATABASE.key, "h2gis");
        params.put(JDBCDataStoreFactory.DBTYPE.key, "h2gis");
        
    }

    @Test
    public void testCanProcess() throws Exception {
        assertFalse(factory.canProcess(Collections.EMPTY_MAP));
        assertTrue(factory.canProcess(params));
    }
    
    @Test
    public void testCreateDataStore() throws Exception {
        JDBCDataStore ds = factory.createDataStore( params );
        assertNotNull( ds );
        assertTrue(ds.getDataSource() instanceof ManageableDataSource);
    }

    
    //@Test Doesn't work yet
    public void testTCP() throws Exception {
        HashMap params = new HashMap();
        params.put(H2GISDataStoreFactory.HOST.key, "localhost");
        params.put(H2GISDataStoreFactory.DATABASE.key, "geotools");
        params.put(H2GISDataStoreFactory.USER.key, "h2gis");
        params.put(H2GISDataStoreFactory.PASSWD.key, "h2gis");
        
        DataStore ds = factory.createDataStore(params);
        try {
            ds.getTypeNames();
            fail("Should not have made a connection.");
        }
        catch(Exception ok) {}
        
        Server server = Server.createTcpServer(new String[]{"-baseDir", "target"});
        server.start();
        try {
            while(!server.isRunning(false)) {
                Thread.sleep(100);
            }
            
            ds = factory.createDataStore(params);
            ds.getTypeNames();
        }
        finally {
            server.shutdown();
        }
    }
}
