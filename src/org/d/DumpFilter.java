package org.d;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class DumpFilter implements Filter {

	private static class ByteArrayServletStream extends ServletOutputStream {

		ByteArrayOutputStream baos;

		ByteArrayServletStream(ByteArrayOutputStream baos) {
			this.baos = baos;
		}

		public void write(int param) throws IOException {
			baos.write(param);
		}
	}

	private static class ByteArrayPrintWriter {

		private ByteArrayOutputStream baos = new ByteArrayOutputStream();

		private PrintWriter pw = new PrintWriter(baos);

		private ServletOutputStream sos = new ByteArrayServletStream(baos);

		public PrintWriter getWriter() {
			return pw;
		}

		public ServletOutputStream getStream() {
			return sos;
		}

		byte[] toByteArray() {
			return baos.toByteArray();
		}
	}

	private class BufferedServletInputStream extends ServletInputStream {

		ByteArrayInputStream bais;

		public BufferedServletInputStream(ByteArrayInputStream bais) {
			this.bais = bais;
		}

		public int available() {
			return bais.available();
		}

		public int read() {
			return bais.read();
		}

		public int read(byte[] buf, int off, int len) {
			return bais.read(buf, off, len);
		}

	}

	private class BufferedRequestWrapper extends HttpServletRequestWrapper {

		ByteArrayInputStream bais;

		ByteArrayOutputStream baos;

		BufferedServletInputStream bsis;

		byte[] buffer;

		public BufferedRequestWrapper(HttpServletRequest req) throws IOException {
			super(req);
			InputStream is = req.getInputStream();
			baos = new ByteArrayOutputStream();
			byte buf[] = new byte[1024];
			int letti;
			while ((letti = is.read(buf)) > 0) {
				baos.write(buf, 0, letti);
			}
			buffer = baos.toByteArray();
		}

		public ServletInputStream getInputStream() {
			try {
				bais = new ByteArrayInputStream(buffer);
				bsis = new BufferedServletInputStream(bais);
			} catch (Exception ex) {
				ex.printStackTrace();
			}

			return bsis;
		}

		public byte[] getBuffer() {
			return buffer;
		}

	}

	public void init(FilterConfig filterConfig) throws ServletException {
		
	}

	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
			throws IOException, ServletException {
		
		long start = System.currentTimeMillis();

		final HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
		BufferedRequestWrapper bufferedRequest = new BufferedRequestWrapper(httpRequest);
		
		String req = new String(bufferedRequest.getBuffer());
		String traceId = String.valueOf(System.nanoTime());
		httpRequest.setAttribute("traceId", traceId);
		Mnp.logger.info(traceId+"|call|"+servletRequest.getRemoteAddr()+"|"+httpRequest.getRequestURL());
		Mnp.logger.info(traceId+"|req|"+req.replaceAll("\n"," ").replaceAll("\r"," "));
		
		final HttpServletResponse response = (HttpServletResponse) servletResponse;

		final ByteArrayPrintWriter pw = new ByteArrayPrintWriter();
		HttpServletResponse wrappedResp = new HttpServletResponseWrapper(response) {
			public PrintWriter getWriter() {
				return pw.getWriter();
			}

			public ServletOutputStream getOutputStream() {
				return pw.getStream();
			}

		};

		filterChain.doFilter(bufferedRequest, wrappedResp);

		byte[] bytes = pw.toByteArray();
		response.getOutputStream().write(bytes);
		String resp = new String(bytes);
		Mnp.logger.info(traceId+"|resp|"+resp.replaceAll("\n"," ").replaceAll("\r"," "));
		
		long end = System.currentTimeMillis();
		Mnp.requests.add(new String[]{traceId,(String) httpRequest.getAttribute("service"),String.valueOf(end-start),httpRequest.getRemoteAddr(),req,resp});
	}

	public void destroy() {
	}

}
