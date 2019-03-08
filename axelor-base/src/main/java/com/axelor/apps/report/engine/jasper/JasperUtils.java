package com.axelor.apps.report.engine.jasper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Useful functions to ease JasperReports usage. */
public class JasperUtils {
  /**
   * Performs a SQL query using the supplied connection and returns its first line as a map. This is
   * useful when you want to spread a query result on different areas of the same report without
   * willing to create as much list as areas.
   *
   * @param connection Connection to use to execute the query (likely $P{REPORT_CONNECTION})
   * @param sql SQL to execute
   * @param params Optional parameters for the query
   * @return null if no line is returned or an exception occurs, the first line of result as a map
   *     alias → value.
   */
  public static Map<String, Object> sqlFetch(Connection connection, String sql, Object... params) {

    try {
      PreparedStatement ps = connection.prepareStatement(sql);
      if (params != null) {
        for (int i = 0; i < params.length; ++i) {
          ps.setObject(i + 1, params[i]);
        }
      }
      ResultSet rs = ps.executeQuery();
      if (rs.next() == false) return null;
      final Map<String, Object> result = new HashMap<>();
      ResultSetMetaData rsmd = rs.getMetaData();
      for (int i = 1; i <= rsmd.getColumnCount(); ++i) {
        result.put(rsmd.getColumnLabel(i), rs.getObject(i));
      }
      rs.close();
      ps.close();
      return result;
    } catch (SQLException sqle) {
      return Collections.singletonMap("exception", sqle);
      // TraceBackService.trace(sqle);
      // return null;
    }
  }

  /**
   * Cleans up an HTML string to make it usable in a TextField with "html" markup by avoiding some
   * glitches. This function obviously does not handle all the cases, you may want to add some
   * specific ones.
   *
   * @param html HTML to cleanup (not null).
   * @return The cleaned up HTML.
   */
  public static String htmlFix(final String html) {
    return html.replaceAll("(?i)<p[^>]+>\\s*</p>|<!--\\w+-->", "")
        .replaceAll("\\bfont-size\\s*:\\s*inherit\\b", "")
        .replaceAll("(?i)<p>\\s*<ul>|<(/)ul></p>", "<$1ul>")
        .replaceAll("(?i)<br/?>\\s*</(li|p)>", "</$1>")
        .replaceAll("(?i)<p", "<p style=\"margin-top: 0; margin-bottom:0.1em\" ");
  }
}
