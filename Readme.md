# Argo Flink

### (不再维护)read功能:

| 功能       | 描述               |   |
|----------|------------------|---|
| 支持的表类型   | holodes表. 不支持rk表 |   |
| filter下推 | int,string,long, |   |
| 部分列读     | 支持               |   |
| 流读(增量读)  | 不支持              |   |
| limit读   | 暂时不支持            |   |
| 版本       | flink1.15        |   |
|          |                  |   |

### write功能:

| 功能     | 描述                                |   |
|--------|-----------------------------------|---|
| 支持的表类型 | holodes表. rk表                     |   |
| 版本     | flink1.10, 1.11, 1.13, 1.14, 1.15 |   |
|        |                                   |   |

## Argo Flink Read

支持的数据格式:

TINYINT,
SMALLINT,
INT,
BIGINT,
FLOAT,
DOUBLE,
DECIMAL(30, 17),
STRING,
VARCHAR(11),
BOOLEAN,
TIMESTAMP

建表语句:

#### ddl:

flink table sql

```sql
SET
sql
-client.execution.result-mode=TABLEAU;
SET
'parallelism.default' = '2';
create table flink_input_rowkey4
(
    c0  tinyint,
    c1  smallint,
    c2  int,
    c3  bigint,
    c4  float,
    c5  double,
    c6  decimal(30, 17),
    c7  string,
    c8  varchar(11),
    c9  boolean,
    c10 timestamp,
) WITH (
      'connector' = 'argodb',
      'master.group' = '172.18.120.33:39630,172.18.120.32:39630,172.18.120.31:39630',
      'table.name' = 'default.table_output_holo_bucket',
      'shiva2.enable' = 'true',
      'metastore.url' = 'jdbc:hive2://172.18.120.32:10000/default'
      );

select c0,
       c1,
       c2,
       c4,
       c5,
       c6,
       c7,
       c8,
       c9,
       c10
from flink_input_rowkey4 limit 2
```

quark tbale sql

```sql
create table table_output_holo_bucket
(
    c0  tinyint,
    c1  smallint,
    c2  int,
    c3  bigint,
    c4  float,
    c5  double,
    c6  decimal(30, 17),
    c7  string,
    c8  varchar(11),
    c9  boolean,
    c10 timestamp,
) stored as holodesk;
```