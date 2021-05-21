package io.dataease.provider.sqlserver;

import io.dataease.base.domain.DatasetTableField;
import io.dataease.controller.request.chart.ChartExtFilterRequest;
import io.dataease.dto.chart.ChartViewFieldDTO;
import io.dataease.provider.QueryProvider;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

/**
 * @Author gin
 * @Date 2021/5/17 2:43 下午
 */
@Service("sqlserverQuery")
public class SqlserverQueryProvider extends QueryProvider {
    @Override
    public Integer transFieldType(String field) {
        switch (field.toUpperCase()) {
            case "CHAR":
            case "VARCHAR":
            case "TEXT":
            case "TINYTEXT":
            case "MEDIUMTEXT":
            case "LONGTEXT":
            case "NCHAR":
            case "NVARCHAR":
            case "NTEXT":
            case "ENUM":
                return 0;// 文本
            case "DATE":
            case "TIME":
            case "YEAR":
            case "DATETIME":
            case "TIMESTAMP":
                return 1;// 时间
            case "INT":
            case "SMALLINT":
            case "MEDIUMINT":
            case "INTEGER":
            case "BIGINT":
                return 2;// 整型
            case "FLOAT":
            case "DOUBLE":
            case "DECIMAL":
                return 3;// 浮点
            case "BIT":
            case "TINYINT":
                return 4;// 布尔
            default:
                return 0;
        }
    }

    @Override
    public String createQueryCountSQL(String table) {
        return MessageFormat.format("SELECT count(*) FROM {0}", table);
    }

    @Override
    public String createQueryCountSQLAsTmp(String sql) {
        return createQueryCountSQL(" (" + sqlFix(sql) + ") AS tmp ");
    }

    @Override
    public String createSQLPreview(String sql, String orderBy) {
        return "SELECT * FROM (" + sqlFix(sql) + ") AS tmp ORDER BY " + orderBy + " LIMIT 0,1000";
    }

    @Override
    public String createQuerySQL(String table, List<DatasetTableField> fields) {
        String[] array = fields.stream().map(f -> {
            StringBuilder stringBuilder = new StringBuilder();
            // 如果原始类型为时间
            if (f.getDeExtractType() == 1) {
                if (f.getDeType() == 2 || f.getDeType() == 3) {
                    stringBuilder.append("unix_timestamp(").append(f.getDataeaseName()).append(")*1000 as ").append(f.getDataeaseName());
                } else {
                    stringBuilder.append(f.getDataeaseName());
                }
            } else {
                if (f.getDeType() == 1) {
                    stringBuilder.append("FROM_UNIXTIME(cast(").append(f.getDataeaseName()).append(" as decimal(20,0))/1000,'%Y-%m-%d %H:%i:%S') as ").append(f.getDataeaseName());
                } else {
                    stringBuilder.append(f.getDataeaseName());
                }
            }
            return stringBuilder.toString();
        }).toArray(String[]::new);

        return MessageFormat.format("SELECT {0} FROM {1} ORDER BY " + (fields.size() > 0 ? fields.get(0).getDataeaseName() : "null"), StringUtils.join(array, ","), table);
    }

    @Override
    public String createQuerySQLAsTmp(String sql, List<DatasetTableField> fields) {
        return createQuerySQL(" (" + sqlFix(sql) + ") AS tmp ", fields);
    }

    @Override
    public String createQuerySQLWithPage(String table, List<DatasetTableField> fields, Integer page, Integer pageSize, Integer realSize) {
        return createQuerySQL(table, fields) + " offset " + (page - 1) * pageSize + " rows fetch next " + realSize + " rows only";
    }

    @Override
    public String createQuerySQLAsTmpWithPage(String sql, List<DatasetTableField> fields, Integer page, Integer pageSize, Integer realSize) {
        return createQuerySQLAsTmp(sql, fields) + " LIMIT " + (page - 1) * pageSize + "," + realSize;
    }

    @Override
    public String getSQL(String table, List<ChartViewFieldDTO> xAxis, List<ChartViewFieldDTO> yAxis, List<ChartExtFilterRequest> extFilterRequestList) {
        // 字段汇总 排序等
        String[] field = yAxis.stream().map(y -> {
            StringBuilder f = new StringBuilder();
            if (StringUtils.equalsIgnoreCase(y.getDataeaseName(), "*")) {
                f.append(y.getSummary()).append("(").append(y.getDataeaseName()).append(")");
            } else {
                if (StringUtils.equalsIgnoreCase(y.getSummary(), "avg") || StringUtils.containsIgnoreCase(y.getSummary(), "pop")) {
                    f.append("CAST(")
                            .append(y.getSummary()).append("(")
                            .append("CAST(").append(y.getDataeaseName()).append(" AS ").append(y.getDeType() == 2 ? "DECIMAL(20,0)" : "DECIMAL(20,2)").append(")")
                            .append(") AS DECIMAL(20,2)").append(")");
                } else {
                    f.append(y.getSummary()).append("(")
                            .append("CAST(").append(y.getDataeaseName()).append(" AS ").append(y.getDeType() == 2 ? "DECIMAL(20,0)" : "DECIMAL(20,2)").append(")")
                            .append(")");
                }
            }
            f.append(" AS _").append(y.getSummary()).append("_").append(StringUtils.equalsIgnoreCase(y.getDataeaseName(), "*") ? "" : y.getDataeaseName());
            return f.toString();
        }).toArray(String[]::new);
        String[] groupField = xAxis.stream().map(x -> {
            StringBuilder stringBuilder = new StringBuilder();
            // 如果原始类型为时间
            if (x.getDeExtractType() == 1) {
                if (x.getDeType() == 2 || x.getDeType() == 3) {
                    stringBuilder.append("unix_timestamp(").append(x.getDataeaseName()).append(")*1000 as ").append(x.getDataeaseName());
                } else {
                    stringBuilder.append(x.getDataeaseName());
                }
            } else {
                if (x.getDeType() == 1) {
                    stringBuilder.append("FROM_UNIXTIME(cast(").append(x.getDataeaseName()).append(" as decimal(20,0))/1000,'%Y-%m-%d %H:%i:%S') as ").append(x.getDataeaseName());
                } else {
                    stringBuilder.append(x.getDataeaseName());
                }
            }
            return stringBuilder.toString();
        }).toArray(String[]::new);
        String[] group = xAxis.stream().map(ChartViewFieldDTO::getDataeaseName).toArray(String[]::new);
        String[] xOrder = xAxis.stream().filter(f -> StringUtils.isNotEmpty(f.getSort()) && !StringUtils.equalsIgnoreCase(f.getSort(), "none"))
                .map(f -> f.getDataeaseName() + " " + f.getSort()).toArray(String[]::new);
        String[] yOrder = yAxis.stream().filter(f -> StringUtils.isNotEmpty(f.getSort()) && !StringUtils.equalsIgnoreCase(f.getSort(), "none"))
                .map(f -> "_" + f.getSummary() + "_" + (StringUtils.equalsIgnoreCase(f.getDataeaseName(), "*") ? "" : f.getDataeaseName()) + " " + f.getSort()).toArray(String[]::new);
        String[] order = Arrays.copyOf(xOrder, xOrder.length + yOrder.length);
        System.arraycopy(yOrder, 0, order, xOrder.length, yOrder.length);

        String[] xFilter = xAxis.stream().filter(x -> CollectionUtils.isNotEmpty(x.getFilter()) && x.getFilter().size() > 0)
                .map(x -> {
                    String[] s = x.getFilter().stream().map(f -> {
                        StringBuilder filter = new StringBuilder();
                        if (x.getDeType() == 1 && x.getDeExtractType() != 1) {
                            filter.append(" AND FROM_UNIXTIME(cast(")
                                    .append(x.getDataeaseName())
                                    .append(" AS decimal(20,0))/1000,'%Y-%m-%d %H:%i:%S') ");
                        } else {
                            filter.append(" AND ").append(x.getDataeaseName());
                        }
                        filter.append(transMysqlFilterTerm(f.getTerm()));
                        if (StringUtils.containsIgnoreCase(f.getTerm(), "null")) {
                        } else if (StringUtils.containsIgnoreCase(f.getTerm(), "in")) {
                            filter.append("('").append(StringUtils.join(f.getValue(), "','")).append("')");
                        } else if (StringUtils.containsIgnoreCase(f.getTerm(), "like")) {
                            filter.append("%").append(f.getValue()).append("%");
                        } else {
                            filter.append("'").append(f.getValue()).append("'");
                        }
                        return filter.toString();
                    }).toArray(String[]::new);
                    return StringUtils.join(s, " ");
                }).toArray(String[]::new);

        String sql = MessageFormat.format("SELECT {0},{1} FROM {2} WHERE 1=1 {3} GROUP BY {4} ORDER BY null,{5}",
                StringUtils.join(groupField, ","),
                StringUtils.join(field, ","),
                table,
                (xFilter.length > 0 ? StringUtils.join(xFilter, " ") : "") + transMysqlExtFilter(extFilterRequestList),// origin field filter and panel field filter
                StringUtils.join(group, ","),
                StringUtils.join(order, ","));
        if (sql.endsWith(",")) {
            sql = sql.substring(0, sql.length() - 1);
        }
        // 如果是对结果字段过滤，则再包裹一层sql
        String[] resultFilter = yAxis.stream().filter(y -> CollectionUtils.isNotEmpty(y.getFilter()) && y.getFilter().size() > 0)
                .map(y -> {
                    String[] s = y.getFilter().stream().map(f -> {
                        StringBuilder filter = new StringBuilder();
                        // 原始类型不是时间，在de中被转成时间的字段做处理
                        if (y.getDeType() == 1 && y.getDeExtractType() != 1) {
                            filter.append(" AND FROM_UNIXTIME(cast(_")
                                    .append(y.getSummary()).append("_").append(StringUtils.equalsIgnoreCase(y.getDataeaseName(), "*") ? "" : y.getDataeaseName())
                                    .append(" AS decimal(20,0))/1000,'%Y-%m-%d %H:%i:%S') ");
                        } else {
                            filter.append(" AND _").append(y.getSummary()).append("_").append(StringUtils.equalsIgnoreCase(y.getDataeaseName(), "*") ? "" : y.getDataeaseName());
                        }
                        filter.append(transMysqlFilterTerm(f.getTerm()));
                        if (StringUtils.containsIgnoreCase(f.getTerm(), "null")) {
                        } else if (StringUtils.containsIgnoreCase(f.getTerm(), "in")) {
                            filter.append("('").append(StringUtils.join(f.getValue(), "','")).append("')");
                        } else if (StringUtils.containsIgnoreCase(f.getTerm(), "like")) {
                            filter.append("%").append(f.getValue()).append("%");
                        } else {
                            filter.append("'").append(f.getValue()).append("'");
                        }
                        return filter.toString();
                    }).toArray(String[]::new);
                    return StringUtils.join(s, " ");
                }).toArray(String[]::new);
        if (resultFilter.length == 0) {
            return sql;
        } else {
            String filterSql = MessageFormat.format("SELECT * FROM {0} WHERE 1=1 {1}",
                    "(" + sql + ") AS tmp",
                    StringUtils.join(resultFilter, " "));
            return filterSql;
        }
    }

    @Override
    public String getSQLAsTmp(String sql, List<ChartViewFieldDTO> xAxis, List<ChartViewFieldDTO> yAxis, List<ChartExtFilterRequest> extFilterRequestList) {
        return getSQL(" (" + sqlFix(sql) + ") AS tmp ", xAxis, yAxis, extFilterRequestList);
    }

    @Override
    public String searchTable(String table) {
        return "SELECT table_name FROM information_schema.TABLES WHERE table_name ='" + table + "'";
    }

    public String transMysqlFilterTerm(String term) {
        switch (term) {
            case "eq":
                return " = ";
            case "not_eq":
                return " <> ";
            case "lt":
                return " < ";
            case "le":
                return " <= ";
            case "gt":
                return " > ";
            case "ge":
                return " >= ";
            case "in":
                return " IN ";
            case "not in":
                return " NOT IN ";
            case "like":
                return " LIKE ";
            case "not like":
                return " NOT LIKE ";
            case "null":
                return " IS NULL ";
            case "not_null":
                return " IS NOT NULL ";
            default:
                return "";
        }
    }

    public String transMysqlExtFilter(List<ChartExtFilterRequest> requestList) {
        if (CollectionUtils.isEmpty(requestList)) {
            return "";
        }
        StringBuilder filter = new StringBuilder();
        for (ChartExtFilterRequest request : requestList) {
            List<String> value = request.getValue();
            if (CollectionUtils.isEmpty(value)) {
                continue;
            }
            DatasetTableField field = request.getDatasetTableField();
            if (field.getDeType() == 1 && field.getDeExtractType() != 1) {
                filter.append(" AND FROM_UNIXTIME(cast(")
                        .append(field.getDataeaseName())
                        .append(" AS decimal(20,0))/1000,'%Y-%m-%d %H:%i:%S') ");
            } else {
                filter.append(" AND ").append(field.getDataeaseName());
            }
            filter.append(" ")
                    .append(transMysqlFilterTerm(request.getOperator()))
                    .append(" ");
            if (StringUtils.containsIgnoreCase(request.getOperator(), "in")) {
                filter.append("('").append(StringUtils.join(value, "','")).append("')");
            } else if (StringUtils.containsIgnoreCase(request.getOperator(), "like")) {
                filter.append("'%").append(value.get(0)).append("%'");
            } else {
                filter.append("'").append(value.get(0)).append("'");
            }
        }
        return filter.toString();
    }

    private String sqlFix(String sql) {
        if (sql.lastIndexOf(";") == (sql.length() - 1)) {
            sql = sql.substring(0, sql.length() - 1);
        }
        return sql;
    }
}