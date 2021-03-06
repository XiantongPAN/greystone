package com.panxiantong;

import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;


@WebServlet(name = "OnlineServlet", urlPatterns = "/OnlineServlet", asyncSupported = true)
public class OnlineServlet extends HttpServlet {

    private boolean running;


    // Keeps all open connections from browsers
    private Map<Integer, ConcurrentLinkedQueue<AsyncContext>> asyncContexts = new ConcurrentHashMap<>();


    private Map<Integer, String> roomMsg = new ConcurrentHashMap<>();


    // Temporary store for messages when arrived
    private BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

    private Thread notifier = new Thread(new Runnable() {
        @Override
        public void run() {
            while (running) {
                try {
                    // Waits until a message arrives
                    String rmAndmsg = messageQueue.take();
                    int room = Integer.parseInt(rmAndmsg.substring(0, 1));
                    String message = rmAndmsg.substring(1, rmAndmsg.length());
                    //System.out.println("test"+message+asyncContexts.size());
                    // Sends the message to all the AsyncContext's response
                    for (AsyncContext asyncContext : asyncContexts.get(room)) {
                        try {
                            ServletResponse response = asyncContext.getResponse();
                            response.setContentType("text/event-stream");
                            response.setCharacterEncoding("UTF-8");
                            sendMessage(asyncContext.getResponse().getWriter(), message);
                        } catch (Exception e) {
                            // In case of exception remove context from map
                            asyncContexts.remove(asyncContext);
                        }
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


            }
        }
    });

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        // Load previous messages from DB into messageStore
        // messageStore.addAll(db.loadMessages(100));

        for (int i = 1; i <= 9; i++) {
            roomMsg.put(i, "77,99");

        }


        // Start thread
        running = true;
        notifier.start();
    }


    @Override
    public void destroy() {
        // Stops thread and clears queue and stores
        running = false;
        asyncContexts.clear();
        messageQueue.clear();
    }


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        // This is for loading home page when user comes for the first time
//        if (request.getParameter("index") != null) {
//            //request.setAttribute("messages", messageStore);
//            request.getRequestDispatcher("/online.jsp?room="+request.getParameter("room")).forward(request, response);
//            return;
//        }

        int room = Integer.parseInt(request.getParameter("room"));

        if ("text/event-stream".equals(request.getHeader("Accept"))) {
            System.out.println(room);
            // This a Tomcat specific - makes request asynchronous
            request.setAttribute("org.apache.catalina.ASYNC_SUPPORTED", true);

            // Set header fields

            final AsyncContext ac = request.startAsync();
            ac.setTimeout(60 * 1000);
            ac.addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) throws
                        IOException {
                    asyncContexts.remove(ac);
                }

                @Override
                public void onTimeout(AsyncEvent event) throws
                        IOException {
                    asyncContexts.remove(ac);
                }

                @Override
                public void onError(AsyncEvent event) throws
                        IOException {
                    asyncContexts.remove(ac);
                }

                @Override
                public void onStartAsync(AsyncEvent event) throws
                        IOException {
                }
            });
            asyncContexts.get(room).add(ac);

            sendMessage(ac.getResponse().getWriter(), roomMsg.get(room));


            request.getRequestDispatcher("/online.jsp").forward(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        response.setContentType("text/event-stream");
//        response.setCharacterEncoding("UTF-8");
//        PrintWriter out = response.getWriter();
//        String msg = "";
//
//
//        System.out.println("!!" + request.getParameter("msg"));
//        out.println("data: " + request.getParameter("msg") + "\n");
//        out.flush();
//        out.close();


        // Set header fields
//        response.setContentType("text/event-stream");
//        response.setHeader("Cache-Control", "no-cache");
//        response.setHeader("Connection", "keep-alive");
//        response.setCharacterEncoding("UTF-8");

        int room = Integer.parseInt(request.getParameter("room"));
        String message = request.getParameter("msg");
        roomMsg.put(room, message);
        System.out.println("m: " + message);
        try {
            messageQueue.put(room + message);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }


    /**
     * Sends messages to client in SSE format.
     * 空白：表示该行是注释，会在处理时被忽略。
     * data：表示该行包含的是数据。以 data 开头的行可以出现多次。所有这些行都是该事件的数据。
     * event：表示该行用来声明事件的类型。浏览器在收到数据时，会产生对应类型的事件。
     * id：表示该行用来声明事件的标识符。
     * retry，表示该行用来声明浏览器在连接断开之后进行再次连接之前的等待时间。
     */
    private void sendMessage(PrintWriter writer, String message) {
        //System.out.println("send"+message);
        //writer.println(message.getId());
        writer.println("data: " + message);
        writer.println();
        writer.flush();
    }

}
