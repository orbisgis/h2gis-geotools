package org.h2gis.geotools;

import org.geotools.jdbc.JDBCDateTestSetup;

@SuppressWarnings("PMD.JUnit4TestShouldUseTestAnnotation") // not yet a JUnit4 test
public class H2GISDateTestSetup extends JDBCDateTestSetup {

    public H2GISDateTestSetup() {
        super(new H2GISTestSetup());
    }

    @Override
    protected void createDateTable() throws Exception {
        String sql = "CREATE SCHEMA \"geotools\";";
        runSafe(sql);

        run("CREATE TABLE \"geotools\".\"dates\" (\"d\" DATE, \"dt\" TIMESTAMP, \"t\" TIME)");

        run(
                "INSERT INTO \"geotools\".\"dates\" VALUES ("
                        + "PARSEDATETIME('2009-06-28', 'yyyy-MM-dd'), "
                        + "PARSEDATETIME('2009-06-28 15:12:41', 'yyyy-MM-dd HH:mm:ss'),"
                        + "PARSEDATETIME('15:12:41', 'HH:mm:ss')  )");

        run(
                "INSERT INTO \"geotools\".\"dates\" VALUES ("
                        + "PARSEDATETIME('2009-01-15', 'yyyy-MM-dd'), "
                        + "PARSEDATETIME('2009-01-15 13:10:12', 'yyyy-MM-dd HH:mm:ss'),"
                        + "PARSEDATETIME('13:10:12', 'HH:mm:ss')  )");

        run(
                "INSERT INTO \"geotools\".\"dates\" VALUES ("
                        + "PARSEDATETIME('2009-09-29', 'yyyy-MM-dd'), "
                        + "PARSEDATETIME('2009-09-29 17:54:23', 'yyyy-MM-dd HH:mm:ss'),"
                        + "PARSEDATETIME('17:54:23', 'HH:mm:ss')  )");
    }

    @Override
    protected void dropDateTable() throws Exception {
        runSafe("DROP TABLE \"geotools\".\"dates\"");
    }
}