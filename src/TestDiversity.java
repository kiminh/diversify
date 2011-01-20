/** Main class to test different diversification algorithms on queries and
 *  candidate data sets.
 * 
 * @author Scott Sanner (ssanner@gmail.com)
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.text.DecimalFormat;

import util.DocUtils;

import diversity.MMR;
import diversity.ResultListSelector;
import diversity.kernel.BM25Kernel;
import diversity.kernel.LDAKernel;
import diversity.kernel.PLSRKernel;
import diversity.kernel.Kernel;
import diversity.kernel.TF;
import diversity.kernel.TFIDF;

public class TestDiversity {

	public final static int MAX_LINE_LENGTH = 80;
	
	//////////////////////////////////////////////////////////////////
	//                          Test Code
	//////////////////////////////////////////////////////////////////
	
	/** TestDiversity.main()
	 * 
	 * Requires 3 arguments: 
	 * 
	 *   arg 1: directory of files to rank
	 *   arg 2: directory for output
	 *   arg 3: query (enclose in 'single quotes')
	 * 
	 * @param args
	 * @throws FileNotFoundException
	 */
	public static void main(String[] args) throws FileNotFoundException {
		
		// Set this to false to turn off display of computations
		ResultListSelector.SHOW_DEBUG = false;

		// Show args
		//for (int i = 0; i < args.length; i++)
		//	System.out.println("arg[" + i + "] = '" + args[i] + "'");
		
		// Select the test set
		String query = null;
		String data_src = null;
		PrintStream ps = null;
		if (args.length != 3 || (args[0].equals("") || args[1].equals("") || args[2].equals(""))) {
			Usage(args);
			System.exit(1);
		} else {
			data_src = args[0];
			ps = new PrintStream(new FileOutputStream(args[1] + "/" + CleanString(args[2]) + ".txt"));
			query = args[2];
		}
		
		// Build a new result list selectors... all use the greedy MMR approach,
		// each simply selects a different similarity metric
		ArrayList<ResultListSelector> tests = new ArrayList<ResultListSelector>();
		
		// Instantiate all the kernels that we will use with the algorithms below
		Kernel TF_kernel    = new TF(true /* query-relevant diversity */);
		Kernel TFIDF_kernel = new TFIDF(true /* query-relevant diversity */);
		Kernel LDA_kernel   = new LDAKernel(15 /* NUM TOPICS - suggest 15 */, true /* spherical */, true /* query-relevant diversity */);
		Kernel PLSR_kernel  = new PLSRKernel(15 /* NUM TOPICS - suggest 15 */, false /* spherical */);
		Kernel BM25_kernel  = 
			new BM25Kernel( /* 0 for any disables effect */
				0.5d /* k1 - doc TF */, 
				0.5d /* k3 - query TF */,
				0.5d /* b - doc length penalty */ );
		
		// Add all MMR test variants (vary lambda and kernels)
		tests.add( new MMR(
				0.5d /* lambda: 0d is all weight on query sim */, 
				TF_kernel /* sim */,
				TF_kernel /* div */ ));
		
		tests.add( new MMR(
				0.5d /* lambda: 0d is all weight on query sim */, 
				TFIDF_kernel /* sim */,
				TFIDF_kernel /* div */ ));
		
		tests.add( new MMR(
				0.5d /* lambda: 0d is all weight on query sim */, 
				BM25_kernel  /* sim */,
				TFIDF_kernel /* div */ )); /* cannot use BM25 for diversity, not symmetric */
		
		tests.add( new MMR(
				0.0d /* lambda: 0d is **all weight** on query sim */, 
				BM25_kernel  /* sim */,
				TFIDF_kernel /* div */ )); /* cannot use BM25 for diversity, not symmetric */

		tests.add( new MMR(
				0.5d /* lambda: 0d is all weight on query sim */, 
				LDA_kernel /* sim */,
				LDA_kernel /* div */ ));

		tests.add( new MMR(
				0.5d /* lambda: 0d is all weight on query sim */, 
				PLSR_kernel /* sim */,
				PLSR_kernel /* div */ ));

		// This method adds documents to each test in tests
		AddDocs(tests, data_src);
		
		// For each test in tests, build a ranked result list w.r.t. query 
		// and display results (both on stdout and exported to a file)
		for (ResultListSelector test : tests) {
			ShowQueryResults(ps, test, data_src, query, 10);
		}
		ps.close();
	}
	
	//////////////////////////////////////////////////////////////////
	//                           Support Code
	//////////////////////////////////////////////////////////////////
	
	public static void AddDocs(ArrayList<ResultListSelector> tests, String file_dir) {
	
		for (ResultListSelector test : tests) {
			// Load docs from a directory
			File dir = new File(file_dir);
			File[] files = dir.listFiles();
			for (File file : files) {
				String content = DocUtils.ReadFile(file);
				if (content == null) {
					System.out.println("Could not read content for: " + file.getName() + "... skipping");
					System.exit(1);
				}
				test.addDoc(file.getName(), content);
			}
		}
	}

	public static void ShowQueryResults(PrintStream ps2, ResultListSelector d, String data_src, String query, int result_sz) {
	
		ArrayList<String> result_list = d.getResultList(query, result_sz);
		PrintStream[] printstreams = new PrintStream[] { System.out, ps2 };
		
		// Output to all printstreams (for now, stdout and a file)
		for (PrintStream ps : printstreams) {
			ps.println("\n// Query: '" + query + "'");
			ps.println("// ===\n// Data source: " + data_src);
			Object query_sim_rep = ((MMR)d)._sim.getObjectRepresentation(query); 
			String query_sim_str = ((MMR)d)._sim.getObjectStringDescription(query_sim_rep);
			query_sim_str = query_sim_str.replace("\n", "\n// ");
			Object query_div_rep = ((MMR)d)._div.getObjectRepresentation(query); 
			String query_div_str = ((MMR)d)._div.getObjectStringDescription(query_div_rep);
			query_div_str = query_div_str.replace("\n", "\n// ");
			ps.println("// ===\n// Sim representation of query: " + query_sim_str);
			ps.println("// ===\n// Div representation of query: " + query_div_str);
			ps.println("// ===\n// Result list: " + d.getDescription() + "\n// ===");
			for (int i = 0; i < result_list.size(); i++) {
				String content = d._docOrig.get(result_list.get(i));
				if (content.length() > MAX_LINE_LENGTH)
					content = content.substring(0,MAX_LINE_LENGTH);
				ps.println((i+1) + "\t" + result_list.get(i)/* + "\t" + content*/);
			}
			ps.println();
		}
	}

	public static String CleanString(String s) {
		StringBuilder sb = new StringBuilder();
		char[] chars = s.toCharArray();
		char last_char = ' ';
		for (int i = 0; i < chars.length; i++) {
			if (chars[i] == ' ' && last_char != ' ')
				sb.append('_');
			else if (Character.isLetterOrDigit(chars[i]))
				sb.append(chars[i]);
			last_char = chars[i];
		}
		return sb.toString();
	}

	public static void Usage(String[] args) {
		System.out.println("\nYou provided arguments:");
		for (int i = 0; i < args.length; i++)
			System.out.println("arg[" + i + "] = '" + args[i] + "'");

		System.out.println("\nUsage: TestDiversity directory_of_files output_dir 'query'");
	}

}
