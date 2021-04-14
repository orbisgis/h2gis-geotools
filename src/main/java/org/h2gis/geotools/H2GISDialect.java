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

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.h2gis.utilities.GeometryMetaData;
import org.h2gis.utilities.GeometryTableUtilities;
import org.h2gis.utilities.dbtypes.DBTypes;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.logging.Level;
import org.geotools.data.jdbc.FilterToSQL;
import org.geotools.util.factory.Hints;
import org.geotools.jdbc.BasicSQLDialect;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.referencing.CRS;
import org.geotools.util.Version;
import org.h2.value.ValueGeometry;
import org.h2gis.utilities.TableLocation;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 *
 * H2GIS dialect
 *
 */
public class H2GISDialect extends BasicSQLDialect {

    final static WKTReader wKTReader = new WKTReader();
    private H2GISDialect delegate;
    Version h2gisVersion, h2Version;

    //geometry type to class map
    final static Map<String, Class> TYPE_TO_CLASS_MAP = new HashMap<String, Class>() {
        {
            put("GEOMETRY", Geometry.class);
            put("POINT", Point.class);
            put("POINTM", Point.class);
            put("POINTZ", Point.class);
            put("POINTZM", Point.class);
            put("LINESTRING", LineString.class);
            put("LINESTRINGM", LineString.class);
            put("LINESTRINGZ", LineString.class);
            put("LINESTRINGZM", LineString.class);
            put("POLYGON", Polygon.class);
            put("POLYGONM", Polygon.class);
            put("POLYGONZ", Polygon.class);
            put("POLYGONZM", Polygon.class);
            put("MULTIPOINT", MultiPoint.class);
            put("MULTIPOINTM", MultiPoint.class);
            put("MULTIPOINTZ", MultiPoint.class);
            put("MULTIPOINTZM", MultiPoint.class);
            put("MULTILINESTRING", MultiLineString.class);
            put("MULTILINESTRINGM", MultiLineString.class);
            put("MULTILINESTRINGZ", MultiLineString.class);
            put("MULTILINESTRINGZM", MultiLineString.class);
            put("MULTIPOLYGON", MultiPolygon.class);
            put("MULTIPOLYGONM", MultiPolygon.class);
            put("MULTIPOLYGONZ", MultiPolygon.class);
            put("MULTIPOLYGONZM", MultiPolygon.class);
            put("GEOMETRYCOLLECTION", GeometryCollection.class);
            put("BYTEA", byte[].class);
        }
    };

    //geometry class to type map
    final static Map<Class, String> CLASS_TO_TYPE_MAP = new HashMap<Class, String>() {
        {
            put(Geometry.class, "GEOMETRY");
            put(Point.class, "POINT");
            put(LineString.class, "LINESTRING");
            put(Polygon.class, "POLYGON");
            put(MultiPoint.class, "MULTIPOINT");
            put(MultiLineString.class, "MULTILINESTRING");
            put(MultiPolygon.class, "MULTIPOLYGON");
            put(GeometryCollection.class, "GEOMETRYCOLLECTION");
            put(LinearRing.class, "LINEARRING");
        }
    };  

    boolean functionEncodingEnabled = true;
    //Since H2GIS 2.0
    boolean estimatedExtentsEnabled = false;

    @Override
    public boolean isAggregatedSortSupported(String function) {
        return "distinct".equalsIgnoreCase(function);
    }

    /**
     * true is the dialect uses the ST_EstimatedExtent function to compute the
     * envelope of the table
     *
     * @return
     */
    public boolean isEstimatedExtentsEnabled() {
        return estimatedExtentsEnabled;
    }

    /**
     * Set to true to use the ST_EstimatedExtent function
     *
     * @param estimatedExtentsEnabled
     */
    public void setEstimatedExtentsEnabled(boolean estimatedExtentsEnabled) {
        this.estimatedExtentsEnabled = estimatedExtentsEnabled;
    }

    /**
     *
     * @param dataStore
     * @param delegate
     */
    public H2GISDialect(JDBCDataStore dataStore, H2GISDialect delegate) {
        super(dataStore);
        this.delegate = delegate;
    }

    public H2GISDialect(JDBCDataStore dataStore) {
        super(dataStore);
    }

    H2GISDialect getDelegate() {
        return delegate;
    }

    /**
     *
     * @return
     */
    public boolean isFunctionEncodingEnabled() {
        return functionEncodingEnabled;
    }

    @Override
    public void initializeConnection(Connection cx) throws SQLException {
        super.initializeConnection(cx);
    }

    @Override
    public boolean includeTable(String schemaName, String tableName,
            Connection cx) throws SQLException {
        if (tableName.equalsIgnoreCase("geometry_columns")) {
            return false;
        } else if (tableName.toLowerCase().startsWith("spatial_ref_sys")) {
            return false;
        }
        return true;
    }

    @Override
    public void encodeGeometryColumn(GeometryDescriptor gatt, String prefix, int srid, Hints hints,
            StringBuffer sql) {
        boolean force2D = hints != null && hints.containsKey(Hints.FEATURE_2D)
                && Boolean.TRUE.equals(hints.get(Hints.FEATURE_2D));

        if (force2D) {
            sql.append("ST_AsBinary(ST_Force2D(");
            encodeColumnName(prefix, gatt.getLocalName(), sql);
            sql.append("))");
        } else {
            sql.append("ST_AsBinary(");
            encodeColumnName(prefix, gatt.getLocalName(), sql);
            sql.append(")");
        }
    }

    @Override
    public void encodeGeometryEnvelope(String tableName, String geometryColumn,
            StringBuffer sql) {
        sql.append("ST_Extent(\"").append(geometryColumn).append("\")");
    }

    @Override
    public Envelope decodeGeometryEnvelope(ResultSet rs, int column,
            Connection cx) throws SQLException, IOException {
        Geometry envelope = (Geometry) rs.getObject(column);
        if (envelope != null) {
            return envelope.getEnvelopeInternal();
        } else {
            // empty one
            return new Envelope();
        }
    }

    @Override
    public Class<?> getMapping(ResultSet columnMetaData, Connection cx)
            throws SQLException {
        String typeName = columnMetaData.getString("TYPE_NAME");
        if ("uuid".equalsIgnoreCase(typeName)) {
            return UUID.class;
        }
        //Add a function to H2GIS to return the good geometry class
        String gType = null;
        if ("geometry".equalsIgnoreCase(typeName)) {
            String columnName = columnMetaData.getString("COLUMN_NAME");
            TableLocation tableLocation = new TableLocation(null, columnMetaData.getString("TABLE_SCHEM"), columnMetaData.getString("TABLE_NAME"), DBTypes.H2GIS);
            GeometryMetaData geomMetata = GeometryTableUtilities.getMetaData(cx, tableLocation, columnName);
            if (geomMetata != null) {
                gType = geomMetata.getGeometryType();
            }
        } else {
            return null;
        }
        // decode the type into
        if (gType == null) {
            return Geometry.class;
        } else {
            Class geometryClass = TYPE_TO_CLASS_MAP.get(gType.toUpperCase());
            if (geometryClass == null) {
                geometryClass = Geometry.class;
            }
            return geometryClass;
        }
    }

    @Override
    public Integer getGeometrySRID(String schemaName, String tableName,
            String columnName, Connection cx) throws SQLException {

        // first attempt, try with the geometry metadata
        Statement statement = null;
        ResultSet result = null;
        int srid = 0;
        if (schemaName == null) {
            schemaName = "PUBLIC";
        }
        TableLocation tableLocation = TableLocation.parse(schemaName + "." + tableName, DBTypes.H2GIS);
        try {
            // try geometry_columns
            try {
                LOGGER.log(Level.FINE, "Geometry srid check; {0} ", tableLocation);
                srid = GeometryTableUtilities.getSRID(cx, tableLocation, columnName);

            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to retrieve information about "
                        + schemaName + "." + tableName + "." + columnName
                        + " from the geometry_columns table, checking the first geometry instead", e);
            } finally {
                dataStore.closeSafe(result);
            }

            // fall back on inspection of the first geometry, assuming uniform srid (fair assumption
            // an unpredictable srid makes the table un-queriable)
            if (srid == 0) {
                String sqlStatement = "SELECT ST_SRID(\"" + columnName + "\") "
                        + "FROM " + tableLocation.toString()
                        + " WHERE \"" + columnName + "\" IS NOT NULL "
                        + "LIMIT 1";
                statement = cx.createStatement();
                result = statement.executeQuery(sqlStatement);
                if (result.next()) {
                    srid = result.getInt(1);
                }
            }
        } finally {
            dataStore.closeSafe(result);
            dataStore.closeSafe(statement);
        }
        return srid;
    }

    @Override
    public int getGeometryDimension(String schemaName, String tableName, String columnName,
            Connection cx) throws SQLException {
        // first attempt, try with the geometry metadata
        Statement statement = null;
        ResultSet result = null;
        int dimension = 0;
        try {
            if (schemaName == null) {
                schemaName = "PUBLIC";
            }

            // try geometry_columns
            try {
                String sqlStatement = "SELECT COORD_DIMENSION FROM GEOMETRY_COLUMNS WHERE " //
                        + "F_TABLE_SCHEMA = '" + schemaName + "' " //
                        + "AND F_TABLE_NAME = '" + tableName + "' " //
                        + "AND F_GEOMETRY_COLUMN = '" + columnName + "'";

                LOGGER.log(Level.FINE, "Geometry srid check; {0} ", sqlStatement);
                statement = cx.createStatement();
                result = statement.executeQuery(sqlStatement);

                if (result.next()) {
                    dimension = result.getInt(1);
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to retrieve information about "
                        + schemaName + "." + tableName + "." + columnName
                        + " from the geometry_columns table, checking the first geometry instead", e);
            } finally {
                dataStore.closeSafe(result);
            }

            // fall back on inspection of the first geometry, assuming uniform srid (fair assumption
            // an unpredictable srid makes the table un-queriable)
            if (dimension == 0) {
                String sqlStatement = "SELECT ST_DIMENSION(\"" + columnName + "\") "
                        + "FROM \"" + schemaName + "\".\"" + tableName + "\" "
                        + "WHERE " + columnName + " IS NOT NULL "
                        + "LIMIT 1";
                result = statement.executeQuery(sqlStatement);
                if (result.next()) {
                    dimension = result.getInt(1);
                }
            }
        } finally {
            dataStore.closeSafe(result);
            dataStore.closeSafe(statement);
        }

        return dimension;
    }

    @Override
    public String getSequenceForColumn(String schemaName, String tableName,
            String columnName, Connection cx) throws SQLException {

        String sequenceName = tableName + "_" + columnName + "_SEQUENCE";

        //sequence names have to be upper case to select values from them
        sequenceName = sequenceName.toUpperCase();
        Statement st = cx.createStatement();
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT * FROM INFORMATION_SCHEMA.SEQUENCES ");
            sql.append("WHERE SEQUENCE_NAME = '").append(sequenceName).append("'");

            dataStore.getLogger().fine(sql.toString());
            ResultSet rs = st.executeQuery(sql.toString());
            try {
                if (rs.next()) {
                    return sequenceName;
                }
            } finally {
                dataStore.closeSafe(rs);
            }
        } finally {
            dataStore.closeSafe(st);
        }

        return null;
    }

    @Override
    public Object getNextSequenceValue(String schemaName, String sequenceName,
            Connection cx) throws SQLException {
        Statement st = cx.createStatement();
        try {
            String sql = "SELECT nextval('" + sequenceName + "')";

            dataStore.getLogger().fine(sql);
            ResultSet rs = st.executeQuery(sql);
            try {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            } finally {
                dataStore.closeSafe(rs);
            }
        } finally {
            dataStore.closeSafe(st);
        }

        return null;
    }

    @Override
    public boolean lookupGeneratedValuesPostInsert() {
        return true;
    }

    @Override
    public Object getLastAutoGeneratedValue(String schemaName, String tableName, String columnName,
            Connection cx) throws SQLException {

        Statement st = cx.createStatement();
        try {
            String sql = "SELECT lastval()";
            dataStore.getLogger().fine(sql);

            ResultSet rs = st.executeQuery(sql);
            try {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            } finally {
                dataStore.closeSafe(rs);
            }
        } finally {
            dataStore.closeSafe(st);
        }

        return null;
    }

    @Override
    public void registerClassToSqlMappings(Map<Class<?>, Integer> mappings) {
        super.registerClassToSqlMappings(mappings);
        // jdbc metadata for geom columns reports DATA_TYPE=1111=Types.OTHER
        mappings.put(Geometry.class, Types.OTHER);
        mappings.put(UUID.class, Types.OTHER);
    }

    @Override
    public void registerSqlTypeNameToClassMappings(
            Map<String, Class<?>> mappings) {
        super.registerSqlTypeNameToClassMappings(mappings);
        mappings.put("GEOMETRY", Geometry.class);
        mappings.put("TEXT", String.class);
        mappings.put("INT8", Long.class);
        mappings.put("INT4", Integer.class);
        mappings.put("BOOL", Boolean.class);
        mappings.put("BIT", Boolean.class);
        mappings.put("CHARACTER", String.class);
        mappings.put("VARCHAR", String.class);
        mappings.put("VARCHAR_IGNORECASE", String.class);
        mappings.put("CHARACTER VARYING", String.class);
        mappings.put("LONGVARCHAR", String.class);
        mappings.put("FLOAT8", Double.class);
        mappings.put("INT", Integer.class);
        mappings.put("FLOAT4", Float.class);
        mappings.put("REAL", Float.class);
        mappings.put("INT2", Short.class);
        mappings.put("TIME", Time.class);
        mappings.put("TIMESTAMP", Timestamp.class);
        mappings.put("UUID", UUID.class);
        mappings.put("DATE", Date.class);
        mappings.put("JSON", String.class);
    }

    @Override
    public void registerSqlTypeToSqlTypeNameOverrides(
            Map<Integer, String> overrides) {
        overrides.put(Types.VARCHAR, "VARCHAR");
        overrides.put(Types.BOOLEAN, "BOOL");
    }

    @Override
    public String getGeometryTypeName(Integer type) {
        return "geometry";
    }

    @Override
    public void encodePrimaryKey(String column, StringBuffer sql) {
        encodeColumnName(null, column, sql);
        sql.append(" SERIAL PRIMARY KEY");
    }

    /**
     * Creates GEOMETRY_COLUMN registrations
     *
     * @param schemaName
     * @param featureType
     * @param cx
     * @throws java.sql.SQLException
     */
    @Override
    public void postCreateTable(String schemaName,
            SimpleFeatureType featureType, Connection cx) throws SQLException {
        schemaName = schemaName != null ? schemaName : "PUBLIC";
        String tableName = TableLocation.parse(schemaName+"."+featureType.getName().getLocalPart(), DBTypes.H2).toString();        
        
        Statement st = null;
        try {
            st = cx.createStatement();

            // register all geometry columns in the database
            for (AttributeDescriptor att : featureType
                    .getAttributeDescriptors()) {
                if (att instanceof GeometryDescriptor) {
                    GeometryDescriptor gd = (GeometryDescriptor) att;
                    // lookup or reverse engineer the srid
                    int srid = 0;
                    if (gd.getUserData().get(JDBCDataStore.JDBC_NATIVE_SRID) != null) {
                        srid = (Integer) gd.getUserData().get(
                                JDBCDataStore.JDBC_NATIVE_SRID);
                    } else if (gd.getCoordinateReferenceSystem() != null) {
                        try {
                            Integer result = CRS.lookupEpsgCode(gd
                                    .getCoordinateReferenceSystem(), true);
                            if (result != null) {
                                srid = result;
                            }
                        } catch (Exception e) {
                            LOGGER.log(Level.FINE, "Error looking up the "
                                    + "epsg code for metadata "
                                    + "insertion, assuming -1", e);
                        }
                    }

                    // setup the dimension according to the geometry hints
                    int dimensions = 2;
                    if (gd.getUserData().get(Hints.COORDINATE_DIMENSION) != null) {
                        dimensions = (Integer) gd.getUserData().get(Hints.COORDINATE_DIMENSION);
                    }

                    // grab the geometry type
                    String geomType = CLASS_TO_TYPE_MAP.get(gd.getType().getBinding());
                    if (geomType == null) {
                        geomType = "GEOMETRY";
                    }

                    String sql = null;
                    //setup the geometry type
                    if (dimensions == 3) {
                        geomType = geomType + "Z";
                    } else if (dimensions == 4) {
                        geomType = geomType + "ZM";
                    } else if (dimensions > 4) {
                        throw new IllegalArgumentException(
                                "H2GIS only supports geometries with 2 and 3 dimensions, current value: "
                                + dimensions);
                    }
                     
                    sql
                            = "ALTER TABLE "
                            + tableName
                            + " "
                            + "ALTER COLUMN "
                            + gd.getLocalName()
                            + " "
                            + "TYPE geometry ("
                            + geomType
                            + ", "
                            + srid
                            + ");";
                    LOGGER.fine(sql);
                    st.execute(sql);
                }
            }
            if (!cx.getAutoCommit()) {
                cx.commit();
            }
        } finally {
            dataStore.closeSafe(st);
        }
    }

    @Override
    public void postDropTable(String schemaName, SimpleFeatureType featureType, Connection cx)
            throws SQLException {
        //Nothing todo it's a view and a view is not editable.
    }

    @Override
    public void encodeGeometryValue(Geometry value, int dimension, int srid, StringBuffer sql)
            throws IOException {
        if (value == null || value.isEmpty()) {
            sql.append("NULL");
        } else {
            if (value instanceof LinearRing) {
                //postgis does not handle linear rings, convert to just a line string
                value = value.getFactory().createLineString(((LinearRing) value).getCoordinateSequence());
            }
            WKTWriter writer = new WKTWriter(dimension);
            String wkt = writer.write(value);
            sql.append("ST_GeomFromText('").append(wkt).append("', ").append(srid).append(")");
        }
    }

    @Override
    public FilterToSQL createFilterToSQL() {
        H2GISFilterToSQL sql = new H2GISFilterToSQL(this);
        sql.setFunctionEncodingEnabled(functionEncodingEnabled);
        return sql;
    }

    @Override
    public boolean isLimitOffsetSupported() {
        return true;
    }

    @Override
    public void applyLimitOffset(StringBuffer sql, int limit, int offset) {
        if (limit >= 0 && limit < Integer.MAX_VALUE) {
            sql.append(" LIMIT ").append(limit);
            if (offset > 0) {
                sql.append(" OFFSET ").append(offset);
            }
        } else if (offset > 0) {
            sql.append(" OFFSET ").append(offset);
        }
    }

    @Override
    public void encodeValue(Object value, Class type, StringBuffer sql) {
        if (byte[].class == type) {
            byte[] b = (byte[]) value;
            if (value != null) {
                //encode as hex string
                sql.append("'");
                for (int i = 0; i < b.length; i++) {
                    sql.append(Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1));
                }
                sql.append("'");
            } else {
                sql.append("NULL");
            }
        } else {
            super.encodeValue(value, type, sql);
        }

    }

    @Override
    public int getDefaultVarcharSize() {
        return -1;
    }

    @Override
    public String[] getDesiredTablesType() {
        return new String[]{"TABLE", "VIEW", "MATERIALIZED VIEW", "SYNONYM", "TABLE LINK", "EXTERNAL"};
    }

    @Override
    public Geometry decodeGeometryValue(GeometryDescriptor gd, ResultSet rs, String column, GeometryFactory gf, Connection cnctn, Hints hints) throws IOException, SQLException {
        Geometry geom = ValueGeometry.get(rs.getBytes(column)).getGeometry();
        if (geom == null) {
            return null;
        }
        return geom;
    }

    @Override
    public List<ReferencedEnvelope> getOptimizedBounds(String schema, SimpleFeatureType featureType, Connection cx) throws SQLException, IOException {
        if (!estimatedExtentsEnabled) {
            return null;
        }
        String tableName = featureType.getTypeName();
        if (dataStore.getVirtualTables().get(tableName) != null) {
            return null;
        }
        TableLocation tableLocation = new TableLocation(null, schema, tableName, DBTypes.H2GIS);

        List<ReferencedEnvelope> result = new ArrayList<ReferencedEnvelope>();
        Savepoint savePoint = null;
        try {
            if (!cx.getAutoCommit()) {
                savePoint = cx.setSavepoint();
            }

            for (AttributeDescriptor att : featureType.getAttributeDescriptors()) {
                if (att instanceof GeometryDescriptor) {
                    // use estimated extent (optimizer statistics)
                    Envelope env = GeometryTableUtilities.getEstimatedExtent(cx, tableLocation, att.getName().getLocalPart()).getEnvelopeInternal();
                    // reproject and merge
                    if (!env.isNull()) {
                        CoordinateReferenceSystem flatCRS
                                = CRS.getHorizontalCRS(
                                        featureType.getCoordinateReferenceSystem());
                        result.add(new ReferencedEnvelope(env, flatCRS));
                    }
                }
            }
        } catch (SQLException e) {
            if (savePoint != null) {
                cx.rollback(savePoint);
            }
            LOGGER.log(
                    Level.WARNING,
                    "Failed to use ST_EstimatedExtent , falling back on envelope aggregation",
                    e);
            return null;
        } finally {
            if (savePoint != null) {
                cx.releaseSavepoint(savePoint);
            }
        }
        return result;
    }

    public void setFunctionEncodingEnabled(boolean functionEncodingEnabled) {
        this.functionEncodingEnabled = functionEncodingEnabled;
    }

    /**
     * Returns the H2GIS version
     *
     * @param conn
     * @return 
     * @throws java.sql.SQLException
     */
    public Version getH2GISVersion(Connection conn) throws SQLException {
        if (h2gisVersion == null) {
            Statement st = null;
            ResultSet rs = null;
            try {
                st = conn.createStatement();
                rs = st.executeQuery("select H2GISversion()");
                if (rs.next()) {
                    h2gisVersion = new Version(rs.getString(1));
                }
            } finally {
                dataStore.closeSafe(rs);
                dataStore.closeSafe(st);
            }
        }
        return h2gisVersion;
    }

    /**
     * Returns the PostgreSQL version
     *
     * @param conn
     * @return
     * @throws java.sql.SQLException
     */
    public Version getPostgreSQLVersion(Connection conn) throws SQLException {
        if (h2Version == null) {
            Statement st = null;
            ResultSet rs = null;
            try {
                st = conn.createStatement();
                rs = st.executeQuery("select H2VERSION()");
                if (rs.next()) {
                    h2gisVersion = new Version(rs.getString(1));
                }
            } finally {
                dataStore.closeSafe(rs);
                dataStore.closeSafe(st);
            }
        }
        return h2Version;
    }
}
