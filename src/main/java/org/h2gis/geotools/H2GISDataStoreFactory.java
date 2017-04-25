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
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.commons.dbcp.BasicDataSource;
import org.geotools.data.jdbc.datasource.DBCPDataSource;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.jdbc.JDBCDataStoreFactory;
import org.geotools.jdbc.SQLDialect;
import org.h2gis.functions.factory.H2GISFunctions;
import org.h2gis.utilities.JDBCUtilities;


/**
 * DataStoreFacotry for H2GIS database.
 *
 * @author Nicolas Fortin
 * @author Erwan Bocher
 *
 */
public class H2GISDataStoreFactory extends JDBCDataStoreFactory {
    /** parameter for database type */
    public static final Param DBTYPE = new Param("dbtype", String.class, "Type", true, "h2gis");
    
    /** parameter for how to handle associations */
    public static final Param ASSOCIATIONS = new Param("Associations", Boolean.class,
            "Associations", false, Boolean.FALSE);

    /** optional user parameter */
    public static final Param USER = new Param(JDBCDataStoreFactory.USER.key, JDBCDataStoreFactory.USER.type, 
            JDBCDataStoreFactory.USER.description, false, JDBCDataStoreFactory.USER.sample);

    /** optional host parameter */
    public static final Param HOST = new Param(JDBCDataStoreFactory.HOST.key, JDBCDataStoreFactory.HOST.type, 
            JDBCDataStoreFactory.HOST.description, false, JDBCDataStoreFactory.HOST.sample);

    /** optional port parameter */
    public static final Param PORT = new Param(JDBCDataStoreFactory.PORT.key, JDBCDataStoreFactory.PORT.type, 
            JDBCDataStoreFactory.PORT.description, false, 9902);

    /**
     * optional parameter to handle MVCC.
     * @link http://www.h2database.com/html/advanced.html#mvcc
     */
    public static final Param MVCC = new Param("MVCC", Boolean.class, "MVCC", false, Boolean.FALSE);
    
    /**
     * base location to store h2 database files
     */
    File baseDirectory = null;

    /**
     * Sets the base location to store h2 database files.
     *
     * @param baseDirectory A directory.
     */
    public void setBaseDirectory(File baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    /**
     * The base location to store h2 database files.
     * @return 
     */
    public File getBaseDirectory() {
        return baseDirectory;
    }
    
    @Override
    protected void setupParameters(Map parameters) {
        super.setupParameters(parameters);

        //remove host and port temporarily in order to make username optional
        parameters.remove(JDBCDataStoreFactory.HOST.key);
        parameters.remove(JDBCDataStoreFactory.PORT.key);
        
        parameters.put(HOST.key, HOST);
        parameters.put(PORT.key, PORT);

        //remove user and password temporarily in order to make username optional
        parameters.remove(JDBCDataStoreFactory.USER.key);
        parameters.remove(PASSWD.key);
        
        parameters.put(USER.key, USER);
        parameters.put(PASSWD.key, PASSWD);
        
        //add user 
        //add additional parameters
        parameters.put(ASSOCIATIONS.key, ASSOCIATIONS);
        parameters.put(DBTYPE.key, DBTYPE);
    }

    @Override
    public String getDisplayName() {
        return "H2GIS";
    }

    @Override
    public String getDescription() {
        return "H2GIS Database";
    }

    @Override
    protected String getDatabaseID() {
        return (String) DBTYPE.sample;
    }

    @Override
    protected String getDriverClassName() {
        return "org.h2.Driver";
    }

    @Override
    protected SQLDialect createSQLDialect(JDBCDataStore dataStore) {
        return new H2GISDialect(dataStore);
    }

    @Override
    protected DataSource createDataSource(Map params, SQLDialect dialect) throws IOException {
        String database = (String) DATABASE.lookUp(params);
        String host = (String) HOST.lookUp(params);
        Boolean mvcc = (Boolean) MVCC.lookUp(params);
        BasicDataSource dataSource = new BasicDataSource();
        
        if (host != null && !host.equals("")) {
            Integer port = (Integer) PORT.lookUp(params);
            if (port != null) {
                dataSource.setUrl("jdbc:h2:tcp://" + host + ":" + port + "/" + database);
            }
            else {
                dataSource.setUrl("jdbc:h2:tcp://" + host + "/" + database);
            }
        } else if (baseDirectory == null) {
            //use current working directory
            dataSource.setUrl("jdbc:h2:" + database + ";AUTO_SERVER=TRUE"
                    + (mvcc != null ? (";MVCC=" + mvcc) : ""));
        } else {
            //use directory specified if the patch is relative
            String location;
            if (!new File(database).isAbsolute()) {
                location = new File(baseDirectory, database).getAbsolutePath();    
            }
            else {
                location = database;
            }

            dataSource.setUrl("jdbc:h2:file:" + location + ";AUTO_SERVER=TRUE"
                    + (mvcc != null ? (";MVCC=" + mvcc) : ""));
        }
        
        String username = (String) USER.lookUp(params);
        if (username != null) {
            dataSource.setUsername(username);
        }
        String password = (String) PASSWD.lookUp(params);
        if (password != null) {
            dataSource.setPassword(password);
        }
        
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setPoolPreparedStatements(false);

        // if we got here the database has been created, now verify it has the H2GIS extension
        // and eventually try to create them
        JDBCDataStore closer = new JDBCDataStore();
        Connection cx = null;
        try {
            cx = dataSource.getConnection();
            //Add the spatial function
            if (!JDBCUtilities.tableExists(cx, "PUBLIC.GEOMETRY_COLUMNS")) {
                H2GISFunctions.load(cx);
            }
        } catch (SQLException e) {
            throw new IOException("Failed to create the target database", e);
        } finally {
            closer.closeSafe(cx);
        }  

        return new DBCPDataSource(dataSource);
    }

    @Override
    protected JDBCDataStore createDataStoreInternal(JDBCDataStore dataStore, Map params)
            throws IOException {
        //check the foreign keys parameter
        Boolean foreignKeys = (Boolean) ASSOCIATIONS.lookUp(params);

        if (foreignKeys != null) {
            dataStore.setAssociations(foreignKeys.booleanValue());
        }

        return dataStore;
    }

    @Override
    protected String getValidationQuery() {
        return "select now()";
    }  
    
    
    

}
