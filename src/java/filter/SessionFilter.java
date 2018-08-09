/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package filter;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Enumeration;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 *
 * @author REVESYSTEMS12
 */
public class SessionFilter implements Filter {

    private static final String ERROR_PATH = "/common/error.jsp";
    private static final String LOGIN_PATH = "/login/login.jsp";

    private static String[] loginAllowedFrom = null;
    private static boolean isAllAllowed = true;

    private static final boolean debug = true;

    private static final int LOGIN_PAGE = 1547;   //      "/login/login.jsp"
    private static final int LOGOUT_PAGE = 1676;    //      "/login/logout.jsp" || "/common/error.jsp"
    private static final int INDEX = 962;          //      "/index.jsp"
    private static final int MATCH_DETAILS = 2245;   //      "/login/matchDetails.jsp"
    private static final int APP_ROOT = 47;           //  "/"

    // The filter configuration object we are associated with.  If
    // this value is null, this filter instance is not currently
    // configured. 
    private FilterConfig filterConfig = null;

    public SessionFilter()
    {
    }

    private void doBeforeProcessing(ServletRequest request, ServletResponse response)
            throws Exception
    {
        if (debug)
        {
            log("SessionFilter1:DoBeforeProcessing");
        }

        // Write code here to process the request and/or response before
        // the rest of the filter chain is invoked.
        // For example, a logging filter might log items on the request object,
        // such as the parameters.
        for (Enumeration en = request.getParameterNames(); en.hasMoreElements();)
        {
            String name = (String) en.nextElement();
            String values[] = request.getParameterValues(name);
            int n = values.length;
            StringBuffer buf = new StringBuffer();
            buf.append(name);
            buf.append("=");
            for (int i = 0; i < n; i++)
            {
                buf.append(values[i]);
                if (i < n - 1)
                {
                    buf.append(",");
                }
            }
            log(buf.toString());
        }

        if (request instanceof HttpServletRequest)
        {
            HttpServletRequest httpReq = (HttpServletRequest) request;
            HttpServletResponse httpRes = (HttpServletResponse) response;
            String userPath = httpReq.getServletPath();
            String contextPath = httpReq.getContextPath();
            System.out.println("User Path : " + userPath + "\nContext Path :" + contextPath);
            
            if(loginSubmitUrl != null && loginSubmitUrl.equals(userPath)){
                if(isAllAllowed){
                    return;
                }
                String referer = httpReq.getHeader("Referer");
                String hostPath = "/";
                if (referer != null)
                {
                    hostPath = new URL(referer).getPath();
                }
                System.out.println("\nhostPath = " + hostPath);
                for (String loginAllowedFrom1 : loginAllowedFrom)
                {
                    System.out.println("Checking with = " + contextPath+loginAllowedFrom1);
                    if (hostPath.equals(contextPath+loginAllowedFrom1))
                    {
                        return;
                    }
                }
                httpRes.sendRedirect(contextPath + loginPagePath+"?mess=Unauthorised Access");
                throw new Exception("Match Details request coming from wrong referer");
            }
            
            HttpSession session = httpReq.getSession(false);
            if (session == null || session.isNew())
            {
                httpRes.sendRedirect(contextPath + loginPagePath+"?mess=Please login first");
                throw new Exception("Session Expired");
            }
            Object obj = session.getAttribute(sessionAttribute);
            if (obj == null)
            {
                httpRes.sendRedirect(contextPath + loginPagePath+"?mess=Please login");
                throw new Exception("Session Expired");
            }
        }
    }

    private void doAfterProcessing(ServletRequest request, ServletResponse response)
            throws IOException, ServletException
    {
        if (debug)
        {
            log("SessionFilter1:DoAfterProcessing");
        }
    }

    /**
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param chain The filter chain we are processing
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException
    {

        if (debug)
        {
            log("SessionFilter1:doFilter()");
        }

        try
        {
            doBeforeProcessing(request, response);
        }
        catch (Exception ex)
        {
            log(ex.toString());
            return;
        }

        Throwable problem = null;
        try
        {
            chain.doFilter(request, response);
        }
        catch (Throwable t)
        {
            // If an exception is thrown somewhere down the filter chain,
            // we still want to execute our after processing, and then
            // rethrow the problem after that.
            problem = t;
            t.printStackTrace();
        }

        doAfterProcessing(request, response);

        // If there was a problem, we want to rethrow it if it is
        // a known type, otherwise log it.
        if (problem != null)
        {
            if (problem instanceof ServletException)
            {
                throw (ServletException) problem;
            }
            if (problem instanceof IOException)
            {
                throw (IOException) problem;
            }
            sendProcessingError(problem, response);
        }
    }

    /**
     * Return the filter configuration object for this filter.
     */
    public FilterConfig getFilterConfig()
    {
        return (this.filterConfig);
    }

    /**
     * Set the filter configuration object for this filter.
     *
     * @param filterConfig The filter configuration object
     */
    public void setFilterConfig(FilterConfig filterConfig)
    {
        this.filterConfig = filterConfig;
    }

    /**
     * Destroy method for this filter
     */
    public void destroy()
    {
    }

    /**
     * Init method for this filter
     */
    private static String loginPagePath;
    private static String sessionAttribute;
    private static String loginSubmitUrl;
    
    public void init(FilterConfig filterConfig)
    {
        this.filterConfig = filterConfig;
        if (filterConfig != null)
        {
            if (debug)
            {
                log("SessionFilter1:Initializing filter");
            }
            String allowedUrls = filterConfig.getInitParameter("loginAllowedFrom");
            log("Allowed Login Urls : "+allowedUrls);
            if (allowedUrls == null || allowedUrls.isEmpty() || allowedUrls.equals("*"))
            {
                isAllAllowed = true;
            }
            else
            {
                isAllAllowed = false;
                loginAllowedFrom = allowedUrls.split(",");
                for (int i = 0; i < loginAllowedFrom.length; i++)
                {
                    loginAllowedFrom[i] = loginAllowedFrom[i].trim();
                }
            }
            String loginURL = filterConfig.getInitParameter("loginUrl");
            if (loginURL == null || loginURL.isEmpty())
            {
                loginPagePath = "/login/login.jsp";
            }
            else
            {
                loginPagePath = loginURL;
            }
            String sessionAttributeName = filterConfig.getInitParameter("sessionAttributeName");
            if (sessionAttributeName == null || sessionAttributeName.isEmpty())
            {
                sessionAttribute = "USER_ID";
            }
            else
            {
                sessionAttribute = sessionAttributeName;
            }
            String loginFormSubmitURL = filterConfig.getInitParameter("loginSubmitUrl");
            if (loginFormSubmitURL == null || loginFormSubmitURL.isEmpty())
            {
                loginSubmitUrl = "/login/matchDetails.jsp";
            }
            else
            {
                loginSubmitUrl = loginFormSubmitURL;
            }
        }
    }

    /**
     * Return a String representation of this object.
     */
    @Override
    public String toString()
    {
        if (filterConfig == null)
        {
            return ("SessionFilter1()");
        }
        StringBuffer sb = new StringBuffer("SessionFilter1(");
        sb.append(filterConfig);
        sb.append(")");
        return (sb.toString());
    }

    private void sendProcessingError(Throwable t, ServletResponse response)
    {
        String stackTrace = getStackTrace(t);

        if (stackTrace != null && !stackTrace.equals(""))
        {
            try
            {
                response.setContentType("text/html");
                PrintStream ps = new PrintStream(response.getOutputStream());
                PrintWriter pw = new PrintWriter(ps);
                pw.print("<html>\n<head>\n<title>Error</title>\n</head>\n<body>\n"); //NOI18N

                // PENDING! Localize this for next official release
                pw.print("<h1>The resource did not process correctly</h1>\n<pre>\n");
                pw.print(stackTrace);
                pw.print("</pre></body>\n</html>"); //NOI18N
                pw.close();
                ps.close();
                response.getOutputStream().close();
            }
            catch (Exception ex)
            {
            }
        }
        else
        {
            try
            {
                PrintStream ps = new PrintStream(response.getOutputStream());
                t.printStackTrace(ps);
                ps.close();
                response.getOutputStream().close();
            }
            catch (Exception ex)
            {
            }
        }
    }

    public static String getStackTrace(Throwable t)
    {
        String stackTrace = null;
        try
        {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            pw.close();
            sw.close();
            stackTrace = sw.getBuffer().toString();
        }
        catch (Exception ex)
        {
        }
        return stackTrace;
    }

    public void log(String msg)
    {
        filterConfig.getServletContext().log(msg);
    }

    private static int getCode(String action)
    {
        int len = action.length();
        int sum = 0;
        for (int i = 0; i < len; i++)
        {
            sum += action.charAt(i);
        }
        return sum;
    }

    public static void main(String[] args)
    {   
//        System.out.println(getCode("addRate"));
//        System.out.println("default".hashCode());
//        System.out.println("child".hashCode());
//        System.out.println("product".hashCode());
        System.out.println("allocated".hashCode());
    }
}
