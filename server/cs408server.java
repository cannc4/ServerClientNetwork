/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package server;

import java.io.*;
import static java.lang.Thread.State.WAITING;
import static java.lang.Thread.sleep;
import java.net.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.util.Calendar;


/**
 *
 * @author ANAN
 */
public class cs408server extends javax.swing.JFrame {

    /*
    NOTES:
    remove from clientlist when done
    append
    
    */
    
    
    
    private ArrayList<clientThread> clientList = new ArrayList<clientThread>();
    private LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<String>();
    private ServerSocket serverSocket;
    private LinkedBlockingQueue<String> events = new LinkedBlockingQueue<String>();
    private Hashtable<String,List<Pair<Character, String>>> eventList = new Hashtable<String,List<Pair<Character, String>>>();
    private Hashtable<String,Calendar> eventTimes = new Hashtable<String,Calendar>();
    private Hashtable<String, Hashtable<String,Character>> relations = new Hashtable<String, Hashtable<String,Character>>();
    boolean b = true;
    
    
    public class Pair<L,R> {

        private L left;
        private R right;

        public Pair(L left, R right) {
            this.left = left;
            this.right = right;
        }

        public L getLeft() { return left; }
        public R getRight() { return right; }

        @Override
        public int hashCode() { return left.hashCode() ^ right.hashCode(); }

        @Override
        public boolean equals(Object o) {
            if (o == null) return false;
            if (!(o instanceof Pair)) return false;
            Pair pairo = (Pair) o;
            return this.left.equals(pairo.getLeft()) && this.right.equals(pairo.getRight());
        }
    }
    
    
    
    Runnable eventSch = new Runnable() {
    @Override
    public synchronized void run(){
        while(true)
        {
            try{ //add some delay, timed thread?
                //get and sleep from events
                
                if(events.size()>0)
                {
                    String event = ""; //->temp
                    event = events.take();
                    String un = event.substring(0, event.indexOf(" |"));
                    List<Pair<Character,String>> temp = new ArrayList<Pair<Character,String>>();
                    event = event.substring(event.indexOf("NEWEVENT")+8, event.length() );
                    if(!eventList.contains(event))
                    {
                        event = un + "#" + event;
                        eventList.put(event, temp);

                        
                        int year = Calendar.getInstance().get(Calendar.YEAR);
                        int month = Integer.parseInt(event.substring(event.indexOf("-")+1, event.indexOf("/"))); //0-11
                        int day = Integer.parseInt(event.substring(event.indexOf("/")+1, event.indexOf("|")));
                        int hour = Integer.parseInt(event.substring(event.indexOf("|")+1, event.indexOf(":")));
                        int minute = Integer.parseInt(event.substring(event.indexOf(":")+1,event.length()));
                        Calendar time = Calendar.getInstance(); 
                        time.set(year, month, day, hour, minute);
                        
                        
                        eventTimes.put(event, time);
                        append("New event created: " + event);
                        appendEvent(event);
                        for(clientThread client : clientList)
                        {
                            Pair<Character, String> tempA = new Pair<Character, String>('A',client.un);
                            Pair<Character, String> tempU = new Pair<Character, String>('U',client.un);
                            Pair<Character, String> tempN = new Pair<Character, String>('N',client.un);
                            if(!eventList.get(event).contains(tempA) && !eventList.get(event).contains(tempU) && !eventList.get(event).contains(tempN))
                                eventList.get(event).add(new Pair('U',client.un));
                        }
                    }
                } 

                Calendar current = Calendar.getInstance();
                
                Enumeration items = eventTimes.keys();
                String currentKey = "";
                
                while(items.hasMoreElements())
                {
                    currentKey = (String)items.nextElement();
                    if(eventTimes.get(currentKey).compareTo(current) <= 0)//the time has come
                    {
                        
                        sendToAttendants(currentKey);
                        append("Event: " + currentKey + " is happening now.");
                        eraseEvent(currentKey);
                        eventList.remove(currentKey);
                        eventTimes.remove(currentKey);
                        
                    }
                }    

            }catch(InterruptedException e){}
    }
    
    }
    };
    
    
    
    Thread consumer = new Thread() {
    @Override
    public void run() {
        while (true) {
            try {
                String message = messages.take();
                
                String text = String.valueOf(message);
                String un = text.substring(0, text.indexOf(" |"));
                
                     
                //do whatever with teh message
                if(message != null)
                {
                    if(text.matches(".*NEWEVENT.*")) //add new event
                    {
                        message = text;
                        events.offer(message);
                    }
                    else if(text.matches(".*ATTENDEVENT.*")) //accept event
                    {
                        String eventName = text.substring(text.indexOf("ATTENDEVENT")+11, text.length());
                        Pair<Character, String> temp = new Pair<Character, String>('A',un);
                        Pair<Character, String> tempU = new Pair<Character, String>('U',un);
                        Pair<Character, String> tempN = new Pair<Character, String>('N',un);
                        if(!eventList.get(eventName).contains(temp))
                        {    
                            eventList.get(eventName).add(temp);
                            if(eventList.get(eventName).contains(tempU))
                                eventList.get(eventName).remove(tempU);

                            if(eventList.get(eventName).contains(tempN))
                                eventList.get(eventName).remove(tempN);
                        }
                    }
                    else if(text.matches(".*REJECTEVENT.*")) // reject event
                    {
                        String eventName = text.substring(text.indexOf("REJECTEVENT")+11, text.length());
                        Pair<Character, String> temp = new Pair<Character, String>('N',un);
                        Pair<Character, String> tempA = new Pair<Character, String>('A',un);
                        Pair<Character, String> tempU = new Pair<Character, String>('U',un);
                        
                        if(!eventList.get(eventName).contains(temp))
                        {    
                            eventList.get(eventName).add(temp);
                            if(eventList.get(eventName).contains(tempU))
                                eventList.get(eventName).remove(tempU);

                            if(eventList.get(eventName).contains(tempA))
                                eventList.get(eventName).remove(tempA);
                        }
                    } 
                    else if(text.matches(".*GETEVENTS.*")) //get event list
                    {
                        Pair<Character, String> currentClient = new Pair<Character, String>('U',un);
                        Pair<Character, String> currentClientA = new Pair<Character, String>('A',un);
                        Pair<Character, String> currentClientN = new Pair<Character, String>('N',un);
                        Enumeration items = eventList.keys();
                        String currentKey = "";
                        while(items.hasMoreElements())
                        {
                            currentKey = (String)items.nextElement().toString();
                            if(!eventList.get(currentKey).contains(currentClient) && !eventList.get(currentKey).contains(currentClientN) && !eventList.get(currentKey).contains(currentClientA) )
                            eventList.get(currentKey).add(new Pair<Character,String>('U',un));
                        }

                        
                        append("Sending " + un + " current events");
                        sendEventsToUsername(un);
                    }
                    else if(text.matches(".*GETATTENDANTS.*")) //get event's attendants
                    {
                        String eventName = text.substring(text.indexOf("GETATTENDANTS")+13, text.length());
                        sendAttendantsToUsername(un, eventName);
                        append("Sending " +un+ " attendant list of event " +eventName);
                    }
                    else if(text.matches(".*GETFRIENDFRAME.*"))
                    {
                        sendFriendFrame(un);
                        append("Sending "+un+" users/friends list");
                    }
                    else if(text.matches(".*FRIENDREQUEST.*"))
                    {
                        String tar =  text.substring(text.indexOf("FRIENDREQUEST")+14, text.length());
                        
                        relations.get(un).put(tar, 'P');
                        relations.get(tar).put(un, 'I');
                        sendFriendFrame(tar);
                        sendFriendFrame(un);
                        
                        append(un+" requested friendship to "+tar);
                    }
                    else if(text.matches(".*FRIENDACCEPT.*"))
                    {
                        
                        String tar = text.substring(text.indexOf("FRIENDACCEPT")+13, text.length());
                        
                        relations.get(un).put(tar, 'F');
                        relations.get(tar).put(un, 'F');
                        sendFriendFrame(tar);
                        sendFriendFrame(un);
                        
                        append(un+" accepts friendship request of "+tar);
                    }
                    else if(text.matches(".*FRIENDREJECT.*"))
                    {
                        
                        String tar = text.substring(text.indexOf("FRIENDREJECT")+13, text.length());
                        
                        relations.get(un).put(tar, 'N');
                        relations.get(tar).put(un, 'N');
                        sendFriendFrame(tar);
                        sendFriendFrame(un);
                        
                        append(un+" rejects friendship request of "+tar);
                    }
                    else
                    {
                        append("Message broadcasted: " + message); //includes username //does runtime stop at the time of exception throw???
                        sendToFriends(message);
                    }
                    
                }
            } catch (InterruptedException | NullPointerException e) {}
        }
    }
    };
           
            

          
        public cs408server() {
        initComponents();
    }
    
    public void updateRelations(String un)
    {
        if(!relations.contains(un)) //the guys already in database -> dont do anything
        {
            List<Pair<Character, String>> temp666 = new ArrayList<Pair<Character, String>>();
            relations.put(un, new Hashtable<String,Character>());
            
            for(clientThread client: clientList)
            { 
                if(!un.equals(client.un))
                {
                    relations.get(client.un).put(un, 'N');
                }
                relations.get(un).put(client.un, 'N');
            }
        }
    }
      

    
    public void sendFriendFrame(String un)
    {
        for (clientThread client : clientList) {
            if(client.un.equals(un)) //not sending to myself
            {
                String FF = "FRIENDFRAME";
                
                Hashtable<String, Character> temp = relations.get(un);
                
                Enumeration items = temp.keys();
                
                while(items.hasMoreElements())
                {
                    String currentKey = (String)items.nextElement();
                    FF += currentKey +"/" + temp.get(currentKey)+ " ";
                    
                }
                FF = FF.substring(0,FF.length()-1);
                client.write(FF);
            }
        }
    }
        
    public void sendAttendantsToUsername(String un, String eventName)
    {
        for (clientThread client : clientList) {
            if(client.un.equals(un)) //not sending to myself
            {
                String attendees = "ATTENDEES" + eventName + " ";
                for(Pair<Character,String> temp2 : eventList.get(eventName))
                {
                    attendees += temp2.right+"/"+temp2.left + " ";
                }
                attendees = attendees.substring( 0,attendees.length()-1);
                client.write(attendees);

            }
        }
    }

    
    public void sendToAttendants(String key)
    {
        List<Pair<Character, String>> attList = eventList.get(key);
        if(eventList.containsKey(key))
        {
            for (Pair<Character, String> temp : attList) {
                if(temp.left == 'A')
                {
                    for(clientThread client : clientList)
                    {
                        if(client.un.equals(temp.right))
                            client.write("SERVER: Event you are attending is now: "+key);
                    }
                }
            }
        }
    }
        
    public void sendEventsToAll()
    {
        for (clientThread client : clientList) {
            Enumeration items = eventList.keys();
            String eventNames = "EVENTLIST ";
            while(items.hasMoreElements())
            eventNames += (String)items.nextElement() + " ";
            if(eventNames.length() != 0)
            {
                eventNames = eventNames.substring(0, eventNames.length()-1);
                client.write(eventNames);
            }
        }
    }
    
    public void sendEventsToUsername(String un)
    {
        for (clientThread client : clientList) {
            if(client.un.equals(un)) //not sending to myself
            {
                Enumeration items = eventList.keys();
                String eventNames = "EVENTLIST ";
                while(items.hasMoreElements())
                eventNames += (String)items.nextElement() + " ";
                if(eventNames.length() != 0)
                {
                    eventNames = eventNames.substring(0, eventNames.length()-1);
                    client.write(eventNames);
                }
            }
        }
    }
    
    public void append(String s) {
        Document doc = BigPane.getDocument();
        s = "\n" +s;
        try {
            doc.insertString(doc.getLength(), s, null);
        } catch (BadLocationException ex) {
        }
    }
    
    public void appendEvent(String s) {
        Document doc = eventsListGUI.getDocument();
        s += "\n";
        try {
            doc.insertString(doc.getLength(), s, null);
        } catch (BadLocationException ex) {
        }
    }
    
    public void eraseEvent(String s){
        Document doc = eventsListGUI.getDocument();
        try {
            doc.remove(doc.getText(0, doc.getLength()).indexOf(s), s.length());
        } catch (BadLocationException ex) {}
        
        
    }
 
    
    
    public void sendToAllButMe(String message) {
        String un = String.valueOf(message);
        un = un.substring(0, un.indexOf(" |")); //parses username
        for (clientThread client : clientList) {
            if(!client.un.equals(un)) //not sending to myself
            {
                client.write(message);
            }
        }
    }
       
    public void sendToFriends(String message)
    {
        String un = String.valueOf(message);
        un = un.substring(0, un.indexOf(" |")); //parses username
            
        Hashtable<String,Character> clientRelation = relations.get(un);
        
        for(clientThread client : clientList)
        {
            if(clientRelation.get(client.un).equals('F'))
                client.write(message);
            
        }
    }
    
    public void endAllThreads()   
    {
        for (clientThread client : clientList) {
            client.TBR = true;
        }
    }
   
        public void sendToAll(String message)  {
            for (clientThread client : clientList) {
                client.write(message);
            }
    }        
    
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(cs408server.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(cs408server.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(cs408server.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(cs408server.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>


        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                    new cs408server().setVisible(true);
            }
        });
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        RunButton = new javax.swing.JButton();
        ListenPort = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        IpAdress = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        BigPane = new javax.swing.JEditorPane();
        jLabel5 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        eventsListGUI = new javax.swing.JEditorPane();
        DisconnectButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        RunButton.setText("Run");
        RunButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RunButtonActionPerformed(evt);
            }
        });

        ListenPort.setText("22222");
        ListenPort.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ListenPortActionPerformed(evt);
            }
        });

        jLabel1.setText("Port: ");

        jLabel2.setText("Swaglord420 Messenger");

        jLabel3.setText("Version: v0.2a");

        jLabel4.setText("  IP:");

        IpAdress.setText("127.0.0.1");
        IpAdress.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                IpAdressActionPerformed(evt);
            }
        });

        jScrollPane1.setViewportView(BigPane);

        jLabel5.setText("Events");

        eventsListGUI.setCursor(new java.awt.Cursor(java.awt.Cursor.TEXT_CURSOR));
        jScrollPane2.setViewportView(eventsListGUI);

        DisconnectButton.setLabel("Disconnect");
        DisconnectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DisconnectButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 372, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ListenPort, javax.swing.GroupLayout.PREFERRED_SIZE, 129, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(IpAdress, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(101, 101, 101)
                                .addComponent(RunButton)
                                .addGap(42, 42, 42)
                                .addComponent(DisconnectButton)))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 35, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(50, 50, 50)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel2)
                            .addComponent(jLabel3)))
                    .addComponent(jLabel5)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(RunButton)
                            .addComponent(jLabel4)
                            .addComponent(IpAdress, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(DisconnectButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(ListenPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel1)))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel5)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 218, Short.MAX_VALUE)
                    .addComponent(jScrollPane2))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents


    private void RunButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RunButtonActionPerformed
        
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1); //make sure this doesnt crash stuff
        //executor.scheduleAtFixedRate(eventSch, 0, 3, TimeUnit.SECONDS);
        new Thread(eventSch).start();

        
        
        DisconnectButton.setEnabled(false);
        String a = ListenPort.getText();
        int p = Integer.parseInt(a);
        
        if(p < 0 || p > 65535)
        {
            append("Port out of range, must be between 0 and 65535");
        }
        else
        {
        
     
        DisconnectButton.setEnabled(true);
        
        if(consumer.getState() != WAITING)
        {
            consumer.setDaemon(true);
            consumer.start(); 
        }

        
        
        RunButton.setEnabled(false);
        String un = "";
        
        try {
            serverSocket = new ServerSocket(Integer.parseInt(ListenPort.getText()));
        } catch (IOException ex) {}
        
        append("Server initializing at: " + IpAdress.getText() + ":" + ListenPort.getText());
        
        Thread accept = new Thread() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Socket s = serverSocket.accept();
                        DisconnectButton.setEnabled(true);
                        
                        
                        ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                        out.flush();
                        ObjectInputStream in = new ObjectInputStream(s.getInputStream());
                        
                        out.writeObject("Welcome to Swaglord420 Messenger\n");
                        
                        boolean TBR = false;
                        String un = "";
                        try {
                        un = String.valueOf(in.readObject());
                        append("New client: " + un + " pending...");
                        } catch (ClassNotFoundException ex) {}
                        
                       
                       
                        for(clientThread client : clientList) //for each client in clientlist
                        {
                            if(client.un.equals(un)) //un already exists
                            {
                                TBR = true;
                            }
                        }
                       
                        
                        
                        if(!TBR)
                        {
                            clientList.add(new clientThread(s,in,out,un));
                            updateRelations(un);
                        }  
                        else
                        {
                            append("Username: " + un + " is already taken, disconnecting...");   
                        try {
                            out.writeObject("Username: " + un + " is already taken, disconnecting...");
                        } catch (IOException e) {
                        }
                        }

                        TBR = false;
                        
                    }catch (IOException | InterruptedException e) {}
                   
                }
            }
        };
        
      //  accept.setDaemon(true);
        accept.start();

        }


    }//GEN-LAST:event_RunButtonActionPerformed

    private void ListenPortActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ListenPortActionPerformed


    }//GEN-LAST:event_ListenPortActionPerformed

    private void IpAdressActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_IpAdressActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_IpAdressActionPerformed

    private void DisconnectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_DisconnectButtonActionPerformed

            sendToAll("Server terminating...");
            append("Server terminating...");
            endAllThreads();
            DisconnectButton.setEnabled(false);
            RunButton.setEnabled(true);
            try {
                serverSocket.close();
            } catch (IOException ex) {}
            messages.clear();
            clientList.clear();

    }//GEN-LAST:event_DisconnectButtonActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        
        FileWriter fw = null;
        try {
            File file = new File("db.txt");
            // if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }   
            fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            
            
            Enumeration items = eventList.keys();
            String currentKey = "";
            while(items.hasMoreElements())
            {
                currentKey = (String)items.nextElement();
                List<Pair<Character,String>> current = eventList.get(currentKey);
                Calendar cal = eventTimes.get(currentKey);
                bw.write("!"+currentKey+"'"+(cal.get(cal.MONTH)+12)%13+ "^"+cal.get(cal.DAY_OF_MONTH)+ "+"+ cal.get(cal.HOUR_OF_DAY)+"%"+cal.get(cal.MINUTE));
                bw.newLine();
                for(Pair<Character,String> temp : current)
                {
                    
                    bw.write(temp.left + " " + temp.right);
                    bw.newLine();
                }
            }

            

            
            
            bw.close();
            
            
            sendToAll("Server terminating...");
            System.exit(1);
        } catch (IOException ex) {}

        
    }//GEN-LAST:event_formWindowClosing

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened

        BufferedReader br = null;
 
        try {

                String sCurrentLine;

                br = new BufferedReader(new FileReader("db.txt"));
                
                Calendar c = Calendar.getInstance();
                List<Pair<Character,String>> temp = new ArrayList<Pair<Character,String>>();
                String eventName = "";
                while ((sCurrentLine = br.readLine()) != null) {
                        
                    if(sCurrentLine.contains("!"))
                    {
                        eventName = sCurrentLine.substring(1, sCurrentLine.indexOf("'"));
                        eventList.put(eventName, temp);
                        int year = Calendar.getInstance().get(Calendar.YEAR);
                        int month = Integer.parseInt(sCurrentLine.substring(sCurrentLine.indexOf("'")+1, sCurrentLine.indexOf("^")))+1;
                        int day = Integer.parseInt(sCurrentLine.substring(sCurrentLine.indexOf("^")+1, sCurrentLine.indexOf("+")));
                        int hr = Integer.parseInt(sCurrentLine.substring(sCurrentLine.indexOf("+")+1, sCurrentLine.indexOf("%")));
                        int min = Integer.parseInt(sCurrentLine.substring(sCurrentLine.indexOf("%")+1));
                        c.set(year, month, day, hr, min);
                        eventTimes.put(eventName, c);
                    }
                    else
                    {
                        char tempC = sCurrentLine.substring(0, sCurrentLine.indexOf(" ")).charAt(0);
                        Pair<Character,String> tP = new Pair(tempC,sCurrentLine.substring(sCurrentLine.indexOf(" ")+1));
                        eventList.get(eventName).add(tP);
                    }
                    
                    
                }

        } catch (IOException e) {} 

        
        
        try{
            if (br != null)br.close();
        } catch (IOException ex) {}
        
        
        
        DisconnectButton.setEnabled(false);           
    }//GEN-LAST:event_formWindowOpened


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JEditorPane BigPane;
    private javax.swing.JButton DisconnectButton;
    private javax.swing.JTextField IpAdress;
    private javax.swing.JTextField ListenPort;
    private javax.swing.JButton RunButton;
    private javax.swing.JEditorPane eventsListGUI;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    // End of variables declaration//GEN-END:variables



 private class clientThread {

        ObjectInputStream in;
        ObjectOutputStream out;
        Socket socket;
        String un;
        boolean TBR; //to be removed
        
        clientThread(Socket s, final ObjectInputStream in, final ObjectOutputStream out, final String un) throws IOException, InterruptedException {
            this.socket = s;
            this.TBR = false;
            this.un = un;
            this.in = in;
            this.out = out;
            
            
            if(!TBR)
            {
                append("Client " + un + " accepted at port: "+socket.getPort());
                Thread read = new Thread() {
                    public void run() {
                        while (!TBR) {
                            try {
                                Object obj = in.readObject();
                                if(obj.equals("dc plos yolomaster420blazeitfgt"))
                                {
                                    try{
                                    int i = 0;
                                    for(clientThread client : clientList) //for each client in clientlist
                                    {
                                        if(client.un.equals(un)) //un already exists
                                        {
                                            clientList.remove(i);
                                        }
                                        i++;
                                    }
                                    i = 0;
                                    }catch(ConcurrentModificationException e){}
                                    
                                    
                                    TBR = true;
                                    append("Client " + un + " terminated the connection.");
                                    
                                    
                                }
                                else if(obj.equals("Ok, Bye!"))
                                {
                                    TBR = true;
                                    append(un+" | "+obj);
                                    
                                    
                                }
                                else
                                {
                                    messages.put(un + " | " +obj); //ali says blah
                                    append("Message queue'd: " + un + " | " + obj);
                                   
                                }
                            } catch (InterruptedException | IOException | ClassNotFoundException e) {}
                        }
                        if(TBR)
                        {
                            try {
                                in.close();
                                out.close();
                                socket.close();
                            } catch (IOException ex) {}
                            
                        }
                    }
                };

                read.setDaemon(true);
                read.start();
            }
        }
        
        

        public void write(String obj) {
            try {
                out.writeObject(obj);
            } catch (IOException e) {
            }
        }
    }










}