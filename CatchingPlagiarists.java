import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Stream;

import javax.swing.border.EtchedBorder;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
/**
 * CatchingPlagiarists.java
 */
public class CatchingPlagiarists extends JFrame implements ActionListener, PropertyChangeListener{
    //Main container panel
    private JPanel container;
    
    //Menu bar
    private JMenuBar menu;
    private JMenu file;
    private JMenu help;
    private JMenuItem about;
    private JMenuItem open;
    private JMenuItem exit;

    //Phrase length specifier
    private JPanel phrasePanel;
    private JSpinner phraseSpinner;
    private JLabel phraseLabel;

    //Threshold specifier
    private JPanel thresholdPanel;
    private JSpinner thresholdSpinner;
    private JLabel thresholdLabel;

    //Directory chooser
    private JLabel fileLabel;
    private JButton openDir;

    //Main run button
    private JButton run;

    //Results display
    private JPanel resultsPanel;
    private JTextArea results;
    private String mainDir;

    //Progress bar
    private JProgressBar progressBar;
    private JLabel progressLabel;

    /**
     * Basic node class for a simple linked list
     */
    private class LLNode{
        String info;
        LLNode next;

        public LLNode(String info){
            this.info = info;
        }
    }

    /**
     * Basic class for holding two files and a hit count
     */
    protected class Comparison{
        String fileOne;
        String fileTwo;
        int hitCount;

        public Comparison(String fileOne, String fileTwo, int hitCount){
            this.fileOne = fileOne.substring(fileOne.lastIndexOf('\\') + 1);
            this.fileTwo = fileTwo.substring(fileTwo.lastIndexOf('\\') + 1);
            this.hitCount = hitCount;
        }
        
        @Override
        public String toString(){
            return "[" + fileOne + "," + fileTwo + "] - " + hitCount + " matches";
        }
    }

    /**
     * Comparator for sorting Comparison classes
     */
    private class HitCountSorter implements Comparator<Comparison>{
        @Override
        public int compare(Comparison c1, Comparison c2){
            return (int)Math.signum(c2.hitCount - c1.hitCount);
        }
    }

    /**
     * Main task class that handles the background execution of the CatchingPlagiarists program
     */
    protected class CPTask extends SwingWorker<Void, Void>{
        private ArrayList<Comparison> comparisons;
        private double elapsedTime;

        /**
         * Main method for this SwingWorker thread
         */
        @Override
        public Void doInBackground(){
            results.setText("");

            long startTime = System.nanoTime();

            try{
                comparisons = getTotalHits(mainDir, (int)phraseSpinner.getValue());
            }catch(Exception e){e.printStackTrace();}

            long endTime = System.nanoTime();
            elapsedTime = Math.round(((double)endTime - (double)startTime)/10_000.0)/100000.0;
            
            return null;
        }

        /**
         * Appends information to the results text area
         */
        @Override
        public void done(){
            String result = "Elapsed time: " + elapsedTime + "s";

            for(int i = 0; i < comparisons.size(); i++){
                result += "\n" + comparisons.get(i);
            }

            results.append(result);
            setCursor(null);

            run.setEnabled(true);
            openDir.setEnabled(true);
            phraseSpinner.setEnabled(true);
            thresholdSpinner.setEnabled(true);
        }

        /**
         * Puts every n-contiguous-word sequences from a given text file into a HashSet, using a double-pointer linked list for tracking.
         * Precondition: the specified file is not empty.
         * 
         * @param file the text file to read from
         * @param phraseLength the length of the word sequences to be returned
         * 
         * @throws FileNotFoundException if the specified file cannot be found
         * 
         * @return a HashSet containing all of the word sequences
         */
        protected HashSet<String> getPhrases(String file, int phraseLength) throws FileNotFoundException{
            HashSet<String> set = new HashSet<String>();
            Scanner sc = new Scanner(new File(file));

            LLNode head = new LLNode(sc.next().replaceAll("[^A-z]","").toLowerCase());
            LLNode tail = head;

            for(int i = 0; i < phraseLength - 1; i++){
                if(sc.hasNext()){
                    tail.next = new LLNode(sc.next().replaceAll("[^A-z]","").toLowerCase());
                    tail = tail.next;
                }else
                    break;
            }

            String phrase = "";

            for(LLNode parse = head; parse != null; parse = parse.next){
                phrase += parse.info;
            }

            set.add(phrase);

            while(sc.hasNext()){
                tail.next = new LLNode(sc.next().replaceAll("[^A-z]","").toLowerCase());
                tail = tail.next;
                head = head.next;
                phrase = "";
                for(LLNode parse = head; parse != null; parse = parse.next){
                    phrase += parse.info;
                }
                set.add(phrase);
            }

            return set;
        }

        /**
         * Puts all file names (excluding subdirectories and special files) from a given directory into an ArrayList.
         * 
         * @param directory the String representation of the directory
         * 
         * @throws IOException if an I/O error occurred during the process
         * 
         * @return an ArrayList containing String representations of every file location within the specified directory
         */
        protected ArrayList<String> getAllFiles(String directory) throws IOException{
            ArrayList<String> list = new ArrayList<String>();

            try(Stream<Path> paths = Files.walk(Paths.get(directory))){
                paths
                .filter(new Predicate<Path>(){
                        public boolean test(Path path){
                            return path.toString().endsWith(".txt");
                        }
                    })
                .forEach(new Consumer<Path>(){
                        public void accept(Path path){
                            list.add(path.toString());
                        }
                    });
            }

            return list;
        }

        /**
         * Gets the number of entries in common between two HashSets
         * 
         * @param set1 the first HashSet to be compared
         * @param set2 the second HashSet to be compared
         * 
         * @return the number of matches between the two HashSets
         */
        protected int getMatches(HashSet<String> set1, HashSet<String> set2){
            Set<String> set = new HashSet<String>(set1);
            set.retainAll(set2);
            return set.size();
        }

        /**
         * Gets the number of hits between all of the files within a specified directory.
         * 
         * @param directory the String representation of the directory
         * @param phraseLength the length of the word sequences to be searched for
         * 
         * @throws IOException if an I/O error occurs during the process
         * 
         * @returns an ArrayList containng all of the Comparisons greater than the hit count specified by the thresholdSpinner
         */
        protected ArrayList<Comparison> getTotalHits(String directory, int phraseLength) throws IOException{
            setProgress(0);
            progressLabel.setText("Initializing...");

            ArrayList<String> files = getAllFiles(directory);
            ArrayList<Comparison> list = new ArrayList<Comparison>();
            ArrayList<HashSet<String>> masterList = new ArrayList<HashSet<String>>();

            for(int i = 0; i < files.size(); i++){
                masterList.add(getPhrases(files.get(i), phraseLength));
            }

            int totalSize = (int)Math.pow(masterList.size(), 2) / 2;
            int count = 0;

            for(int j = 0; j < masterList.size() - 1; j++){
                String fileOne = files.get(j).substring(files.get(j).lastIndexOf("\\") + 1);
                for(int k = j + 1; k < masterList.size(); k++){
                    String fileTwo = files.get(k).substring(files.get(k).lastIndexOf("\\") + 1);

                    count++;
                    setProgress(100 * count / totalSize);
                    progressLabel.setText("Comparing " + fileOne + " with " + fileTwo + "...");

                    int matches = getMatches(masterList.get(j), masterList.get(k));

                    if(matches >= (int)thresholdSpinner.getValue())
                        list.add(new Comparison(files.get(j), files.get(k), matches)); //if there are greater than or equal to the number of matches specified by the thresholdSpinner
                }
            }

            Collections.sort(list, new HitCountSorter());

            setProgress(100);
            progressLabel.setText("Done!");

            return list;
        }
    }

    /**
     * Handles button clicks and menu selections
     * 
     * @param evt the user-generated ActionEvent to process
     */
    @Override
    public void actionPerformed(ActionEvent evt){
        String command = evt.getActionCommand();

        if(command.equals("openFile")){
            JFileChooser chooser = new JFileChooser(".");
            chooser.setDialogTitle("Choose a directory.");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            int choice = chooser.showOpenDialog(null);

            if(choice == JFileChooser.APPROVE_OPTION){
                fileLabel.setText(chooser.getSelectedFile().getAbsolutePath());
                mainDir = chooser.getSelectedFile().getPath();
            }
        }else if(command.equals("runProgram") && mainDir != null){
            run.setEnabled(false);
            openDir.setEnabled(false);
            phraseSpinner.setEnabled(false);
            thresholdSpinner.setEnabled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            try{
                CPTask task = new CPTask();
                task.addPropertyChangeListener(this);
                task.execute();

            }catch(Exception e){ e.printStackTrace();}
        }else if(command.equals("closeWindow")){
            this.setVisible(false);
            this.dispose();
        }else if(command.equals("displayAbout")){
            JOptionPane.showMessageDialog(this, "Catching Plagiarists\nBy Joshua Tang\nVer. 1.2\nLast modified: May 20, 2019", "About Catching Plagiarists", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Handles progress reporting
     * 
     * @param evt the PropertyChangeEvent indicating amount of progress done
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt){
        if(evt.getPropertyName().equals("progress")){
            int progress = (int)evt.getNewValue();
            progressBar.setValue(progress);
        }else if(evt.getPropertyName().equals("state")){
            if(evt.getNewValue().toString().equals("STARTED")) progressBar.setValue(0);
            else if(evt.getNewValue().toString().equals("DONE")) progressBar.setValue(100);
        }
    }

    /**
     * Default constructor which will run the CatchingPlagiarists program
     */
    public CatchingPlagiarists() throws Exception{
        //Initializing the main JFrame
        this.setName("Catching Plagiarists");
        this.setResizable(false);
        this.setSize(700, 300);

        //Initializing the main container - will hold every other panel in the GUI
        container = new JPanel();
        container.setPreferredSize(new Dimension(700, 300));
        container.setBackground(new Color(192, 192, 192));
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        
        //Initializing the menu bar
        menu = new JMenuBar();
        help = new JMenu("Help");
        file = new JMenu("File");
        about = new JMenuItem("About");
        about.addActionListener(this);
        about.setActionCommand("displayAbout");
        exit = new JMenuItem("Exit");
        exit.addActionListener(this);
        exit.setActionCommand("closeWindow");
        open = new JMenuItem("Open");
        open.addActionListener(this);
        open.setActionCommand("openFile");
        help.add(about);
        file.add(exit);
        file.add(open);
        menu.add(file);
        menu.add(help);
        this.setJMenuBar(menu);
        
        //Initializing all of the components regarding phrase length selection
        phrasePanel = new JPanel();
        phrasePanel.setLayout(new BoxLayout(phrasePanel, BoxLayout.X_AXIS));
        phraseSpinner = new JSpinner(new SpinnerNumberModel(4, 2, 20, 1));
        phraseLabel = new JLabel("Phrase length");
        phrasePanel.add(phraseLabel);
        phrasePanel.add(phraseSpinner);
        container.add(phrasePanel);

        //Initializing all of the components regarding the threshold specifier
        thresholdPanel = new JPanel();
        thresholdPanel.setLayout(new BoxLayout(thresholdPanel, BoxLayout.X_AXIS));
        thresholdSpinner = new JSpinner(new SpinnerNumberModel(10, 5, 1000, 1));
        thresholdLabel = new JLabel("Minimum number of matches");
        thresholdPanel.add(thresholdLabel);
        thresholdPanel.add(thresholdSpinner);
        container.add(thresholdPanel);
        
        //Adding some blank space for design
        container.add(Box.createRigidArea(new Dimension(0, 10)));

        //Initializing all of the components regarding directory selection
        openDir = new JButton("Open");
        openDir.setActionCommand("openFile");
        openDir.addActionListener(this);
        openDir.setAlignmentX(JButton.CENTER_ALIGNMENT);
        fileLabel = new JLabel("No file selected", JLabel.CENTER);
        fileLabel.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        container.add(openDir);
        container.add(fileLabel);
        
        //Adding some blank space for design
        container.add(Box.createRigidArea(new Dimension(0, 10)));

        //Initializing all of the components regarding the main run button
        run = new JButton("Run");
        run.setActionCommand("runProgram");
        run.addActionListener(this);
        run.setAlignmentX(JButton.CENTER_ALIGNMENT);
        run.setPreferredSize(new Dimension(80, 50));
        container.add(run);
        
        //Adding some blank space for design
        container.add(Box.createRigidArea(new Dimension(0, 10)));

        //Initializing all components regarding the results display
        resultsPanel = new JPanel();
        results = new JTextArea(6, 50);
        results.setBorder(new EtchedBorder(EtchedBorder.RAISED));
        JScrollPane scrollPane = new JScrollPane(results);
        results.setEditable(false);
        container.add(scrollPane);

        //Initializing all components regarding the status display
        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressLabel = new JLabel("Status", JLabel.CENTER);
        progressLabel.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        container.add(progressBar);
        container.add(progressLabel);

        this.add(container);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.pack();
        this.setVisible(true);
    }

    /**
     * The main method for CatchingPlagarists.
     */
    public static void main(String[] args){
        SwingUtilities.invokeLater(new Runnable(){
                public void run(){
                    try{
                        CatchingPlagiarists program = new CatchingPlagiarists();
                    }catch(Exception e){}
                }
            });
    }
}
