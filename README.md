# h2gis-geotools
H2GIS Datastore for the geotools library


## Maven Nexus respository

h2gis-geotools release is available on Maven repository.
Snaphots are available on sonatype repository.


You can get current project snapshot here:http://nexus.orbisgis.org/#view-repositories;osgi-maven-snapshot~browseindex

or add to your pom.xml:

```xml
<repositories>
      <repository>
            <id>orbisgis-release</id>
            <url>https://oss.sonatype.org/content/repositories/releases/</url>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
            <releases>
                <enabled>true</enabled>
            </releases>
        </repository>
        <repository>
            <id>orbisgis-snapshot</id>
            <url>https://oss.sonatype.org/content/repositories/snapshots/</url>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
            <releases>
                <enabled>false</enabled>
            </releases>
        </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.h2gis</groupId>
        <artifactId>h2gis-geotools</artifactId>
        <version>0.2.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```
The snapshot version is compatible with Geotools 26.X


## Example

```java

H2GISDataStoreFactory factory = new H2GISDataStoreFactory();
factory.setBaseDirectory(new File("./target/testH2"));
HashMap params = new HashMap();
params.put(JDBCDataStoreFactory.NAMESPACE.key, "http://www.h2gis.org/test");
params.put(JDBCDataStoreFactory.DATABASE.key, "mydatabase");
params.put(JDBCDataStoreFactory.DBTYPE.key, "h2gis");
JDBCDataStore ds = factory.createDataStore( params );
Statement st = ds.getDataSource().getConnection().createStatement();
st.execute("drop table if exists FORESTS");
st.execute("CREATE TABLE FORESTS ( FID INTEGER, NAME CHARACTER VARYING(64), THE_GEOM GEOMETRY(MULTIPOLYGON));
INSERT INTO FORESTS VALUES(109, 'Green Forest', ST_MPolyFromText( 'MULTIPOLYGON(((28 26,28 0,84 0,84 42,28 26), 
(52 18,66 23,73 9,48 6,52 18)),((59 18,67 18,67 13,59 13,59 18)))', 101));");

SimpleFeatureSource fs = (SimpleFeatureSource) ds.getFeatureSource("FORESTS");
SimpleFeatureType schema = fs.getSchema();
Query query = new Query(schema.getTypeName(), Filter.INCLUDE);

System.out.println(fs.getCount(query));
System.out.println(schema.getAttributeCount());
```
