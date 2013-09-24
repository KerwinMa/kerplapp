/**
 * 
 */
package net.binaryparadox.kerplapp;

import java.io.IOException;
import java.util.Map;

import android.os.Handler;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author daniel
 *
 */
public class KerplappHTTPD extends NanoHTTPD
{
  private final Handler handler = new Handler();
  private final int port;

  public KerplappHTTPD(final int port) throws IOException 
  {
    super(port);
    this.port = port;
  }
  
  @Override
  public Response serve(String uri, Method method, Map<String, String> header, Map<String, String> parms, Map<String, String> files) {
      StringBuilder sb = new StringBuilder();
      sb.append("<html>");
      sb.append("<head><title>Debug Server</title></head>");
      sb.append("<body>");
      sb.append("<h1>Response</h1>");
      sb.append("<p><blockquote><b>URI -</b> ").append(uri).append("<br />");
      sb.append("<b>Method -</b> ").append(method).append("</blockquote></p>");
      sb.append("<h3>Headers</h3><p><blockquote>").append(header).append("</blockquote></p>");
      sb.append("<h3>Parms</h3><p><blockquote>").append(parms).append("</blockquote></p>");
      sb.append("<h3>Files</h3><p><blockquote>").append(files).append("</blockquote></p>");
      sb.append("</body>");
      sb.append("</html>");
      return new Response(sb.toString());
  }
}
