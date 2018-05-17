# PASS NIHMS Submission ETL
The NIHMS Submission ETL contains the components required to download, transform and load Submission information from NIHMS to PASS. The project includes two command line tools. The first uses Selenium to log into the NIHMS website and download the CSV(s) containing compliant, non-compliant, and in-process publication information. The second tool reads those files, transforms the data to the PASS data model and then loads them to PASS.

## NIHMS Data Harvest CLI
The NIHMS Data Harvest CLI uses Selenium with the FireFox drivers to log in and navigate the NIH PACM website. 

### Pre-requisites
The following are required to run this tool:
* Download the latest nihms-data-harvest-cli-{version}-shaded.jar from the [releases page](https://github.com/OA-PASS/nihms-submission-etl/releases) and place in a folder on the machine where the application will run.
* Install FireFox (version 55 or later) on the machine that will run the harvester. You do not need to be able to open the browser, the application will run in "headless" mode.
* Download the [geckodriver](https://github.com/mozilla/geckodriver/releases/tag/v0.20.1) (v0.20.1 has been tested) unzip the folder, locate the geckodriver executable and move it to a convenient location.
* Get an account for the NIH PACM website
* Create a data folder that files will be downloaded to.

### Data Harvest Configuration
There are several ways to configure the Data Harvest CLI. You can use a configuration file, environment variables, system variables, or a combination of these. The configuration file will set system properties. In the absence of a config file, system properties will be used, and in the absence of those, environment variables will be used. Note that to use environment variables, the system property name must be converted to upper case, and the periods replaced with underscores. For example, to define `nihmsetl.data.dir` as an environment variable, use `NIHMSETL_DATA_DIR` instead.

By default, the application will look for a configuration file named `nihms-harvest.properties` in the folder containing the java application. You can override the location of the properties file by defining an environment variable for `nihmsetl.harvester.configfile` e.g. 
```
> java -Dnihmsetl.harvester.configfile=/path/to/configfile.properties -jar nihms-data-harvest-cli-1.0.0-SNAPSHOT-shaded.jar 
```

The configuration file should look like this: 
```
nihmsetl.data.dir=/path/to/pass/loaders/data
webdriver.gecko.driver=/path/to/geckodriver.exe
nihmsetl.harvester.username=username
nihmsetl.harvester.password=password
```

* `nihmsetl.data.dir` is the path where downloaded files will be saved to. If a path is not defined, a `/data` folder will be created in the folder containing the Java application. 
* `webdriver.gecko.driver` is the location of the geckodriver executable. It defaults to the folder containing the Java application 
* `nihmsetl.harvester.username` and `nihmsetl.harvester.password` are the credentials for the NIH PACM site.

### Running the Data Harvester
Once the Data Harvest CLI has been configured, there are a few additional options you can add when running from the command line.

By default all 3 publication statuses - compliant, non-compliant, and in-process CSVs will be downloaded. To download one or two of them, you can add them individually at the command line:
* `-c, -compliant, --compliant` - Download compliant publication CSV. 
* `-p, -inprocess, --inprocess` - Download in-process publication CSV. 
* `-n, -noncompliant, --noncompliant` - Download non-compliant publication CSV. 

You can also specify a start date, by default the PACM system sets the start date to 1 year prior to the date of the download. You can change this by adding a start date parameter:
* `-s, -startDate --startDate` - This will return all records published since the date provided. The syntax is `mm-yyyy`.

So, for example, to download the compliant publications published since December 2012, you would do the following:
```
> java -jar nihms-data-harvest-cli-1.0.0-SNAPSHOT-shaded.jar -s 12-2012 -c
```

On running this command, files will be downloaded and renamed with a prefix according to their status ("compliant", "noncompliant", or "inprocess") and a timestamp integer e.g. `noncompliant_nihmspubs_20180507104323.csv`.

## NIHMS Data Transform-Load CLI
The NIHMS Data Transform-Load CLI reads data in from CSVs that were downloaded from the PACM system, converts them to PASS compliant data and loads them into the PASS database. 

### Pre-requisites
The following is required to run this tool:
* Download latest nihms-data-transform-load-cli-{version}-shaded.jar from the [releases page](https://github.com/OA-PASS/nihms-submission-etl/releases) and place in a folder on the machine where the application will run. 

### Data Transform-Load Configuration
There are several ways to configure the Data Transform-Load CLI. You can use a configuration file, environment variables, system variables, or a combination of these. The configuration file will set system properties. In the absence of a config file, system properties will be used, and in the absence of those, environment variables will be used.

By default, the application will look for a configuration file named `nihms-loader.properties` in the folder containing the java application. You can override the location of the properties file by defining an environment variable for `nihmsetl.harvester.configfile` e.g. 
```
> java -Dnihmsetl.loader.configfile=/path/to/configfile.properties -jar nihms-data-transform-load-cli-1.0.0-SNAPSHOT-shaded.jar 
```

The configuration file should look like this: 
```
nihmsetl.data.dir=/path/to/pass/loaders/data
nihmsetl.repository.uri=https://example:8080/fcrepo/rest/repositories/aaa/bbb/ccc
nihmsetl.pmcurl.template=https://www.ncbi.nlm.nih.gov/pmc/articles/%s/
pass.fedora.baseurl=http://localhost:8080/fcrepo/rest/
pass.fedora.user=admin
pass.fedora.password=password
pass.elasticsearch.url=http://localhost:9200/pass/
pass.elasticsearch.limit=200
```

* `nihmsetl.data.dir` is the path that the CSV files will be read from. If a path is not defined, the app will look for a `/data` folder in the folder containing the java app.
* `nihmsetl.repository.uri` the URI for the Repository resource in PASS that represents the PMC repository.
* `nihmsetl.pmcurl.template` is the template URL used to construct the RepositoryCopy.accessUrl. The article PMC is passed into this URL.
* `pass.fedora.baseurl` - Base URL for Fedora
* `pass.fedora.user` - User name for Fedora access
* `pass.fedora.password` - Password for Fedora access
* `pass.elasticsearch.url` - Base URL for elasticsearch 
* `pass.elasticsearch.limit` - Maximum number of results to return in an Elasticsearch query. This is optional, it defaults to 200 and typically will not need to be overridden.

### Running the Data Transform-Load
Once the Data Transform-Load CLI has been configured, there are a few additional options you can add when running from the command line.

By default all 3 publication statuses - compliant, non-compliant, and in-process CSVs will be downloaded. To download one or two of them, you can add them individually at the command line:
* `-c, -compliant, --compliant` - Download compliant publication CSV. 
* `-p, -inprocess, --inprocess` - Download in-process publication CSV. 
* `-n, -noncompliant, --noncompliant` - Download non-compliant publication CSV. 

So, for example, to process non-compliant spreadsheets only:
```
> java -jar nihms-data-transform-load-cli-1.0.0-SNAPSHOT-shaded.jar -n
```

When run, each row will be loaded into the application and new Publications, Submissions, and RepositoryCopies will be created in PASS as needed. The application will also update any Deposit.repositoryCopy links where a new one is discovered. Once a CSV file has been processed, it will be renamed with a suffix of ".done" e.g. `noncompliant_nihmspubs_20180507104323.csv.done`. To re-process the file, simply rename it to remove the `.done` suffix and re-run the application.