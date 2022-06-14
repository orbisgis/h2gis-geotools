package org.h2gis.geotools;

import org.geotools.jdbc.JDBCPrimaryKeyFinderViewTestSetup;

public class H2GISPrimaryKeyFinderViewTestSetup extends JDBCPrimaryKeyFinderViewTestSetup {

    protected H2GISPrimaryKeyFinderViewTestSetup() {
        super(new H2GISTestSetup());
    }

    @Override
    protected void createMetadataTable() throws Exception {
        run(
                "CREATE TABLE tbl_gt_pk_metadata ( "
                        + "table_schema VARCHAR, "
                        + "table_name VARCHAR NOT NULL, "
                        + "pk_column VARCHAR NOT NULL, "
                        + "pk_column_idx INTEGER, "
                        + "pk_policy VARCHAR, "
                        + "pk_sequence VARCHAR)");
    }

    @Override
    protected void createMetadataView() throws Exception {
        run("CREATE VIEW \"gt_pk_metadata\" AS SELECT * FROM tbl_gt_pk_metadata");
    }

    @Override
    protected void createSequencedPrimaryKeyTable() throws Exception {
        run(
                "CREATE TABLE \"seqtable\" ( \"key\" int PRIMARY KEY, "
                        + "\"name\" VARCHAR, \"geom\" GEOMETRY(GEOMETRY, 4326))");
        run("CREATE SEQUENCE pksequence START WITH 1");

        run(
                "INSERT INTO \"seqtable\" (\"key\", \"name\",\"geom\" ) VALUES ("
                        + "(SELECT NEXTVAL('pksequence')),'one',NULL)");
        run(
                "INSERT INTO \"seqtable\" (\"key\", \"name\",\"geom\" ) VALUES ("
                        + "(SELECT NEXTVAL('pksequence')),'two',NULL)");
        run(
                "INSERT INTO \"seqtable\" (\"key\", \"name\",\"geom\" ) VALUES ("
                        + "(SELECT NEXTVAL('pksequence')),'three',NULL)");

        run(
                "INSERT INTO tbl_gt_pk_metadata VALUES"
                        + "(NULL, 'seqtable', 'key', 0, 'sequence', 'pksequence')");
    }

    @Override
    protected void dropSequencedPrimaryKeyTable() throws Exception {
        run("DROP TABLE \"seqtable\"");
        run("DROP SEQUENCE pksequence");
    }

    @Override
    protected void dropMetadataTable() {
        runSafe("DROP TABLE tbl_gt_pk_metadata");
    }

    @Override
    protected void dropMetadataView() {
        runSafe("DROP VIEW \"gt_pk_metadata\"");
    }
}
