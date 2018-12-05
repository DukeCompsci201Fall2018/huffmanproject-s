import java.util.PriorityQueue;

/**
 * Although this class has a history of several years, it is starting from a
 * blank-slate, new and clean implementation as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information and including
 * debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD);
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;

	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in  Buffered bit stream of the file to be compressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out) {
		// get character frequencies
		int[] counts = readForCounts(in);
		
		// create the Huffman trie
		HuffNode root = makeTreeFromCounts(counts);
		
		//  create encodings for each eight-bit character chunk
		String[] codings = makeCodingsFromTree(root);
		
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		// debug printing
		if (myDebugLevel >= DEBUG_HIGH) {
			System.out.printf("Wrote magic number %d \n", HUFF_TREE);
		}
		writeHeader(root,out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}

	/**
	 * Determine the frequency of every eight-bit character/chunk in the file being compressed
	 * @param in	the file being compressed
	 * @return		an array of frequencies where the index of the array represents the value
	 * and the value at that index represents how often that value occurs
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] counts = new int[ALPH_SIZE + 1];
		
		while (true) {
			int value = in.readBits(BITS_PER_WORD);
			if (value == -1) break;
			counts[value] = counts[value]+1;
			
		}
		counts[PSEUDO_EOF] = 1;
		
		// debug printing
		if(myDebugLevel >= DEBUG_HIGH) {
			for(int i = 0; i < counts.length; i++)
			if(counts[i] != 0) System.out.println(counts[i]);
			System.out.println("EOF: " + counts[PSEUDO_EOF]);
		}
		
		return counts;
	}
	
	/**
	 * Uses a greedy algorithm and a priority queue of HuffNode objects to create the Huffman trie
	 * from the frequencies of every eight-bit character chunk used to create encodings for each character.
	 * @param counts	the frequencies of each character chunk
	 * @return	the root node of the HUffman trie
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {

		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		// for every index such that freq[index] > 0, make a node and add to the queue
		for(int index = 0; index < counts.length; index++) {
			if(counts[index] > 0)
			pq.add(new HuffNode(index,counts[index],null,null));
		}
		
		// debug printing
		if (myDebugLevel >= DEBUG_HIGH) {
			System.out.printf("pq ceated with %d nodes \n", pq.size());
		}

		// remove the minimal-weight nodes and combine them
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
    		HuffNode right = pq.remove();
    		pq.add(new HuffNode(0, left.myWeight+right.myWeight, left, right));
		}
		
		// root is the root node of our Huffman trie
		HuffNode root = pq.remove();
		
		return root;
	}

	/**
	 * Use the Huffman trie to create the encodings for each eight-bit character chunk
	 * @param root	the root HuffNode of the Huffman trie
	 * @return an array of Strings such that array[val] is the encoding of the 8-bit chunk val
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
	    codingHelper(root,"",encodings);
	    
		return encodings;
	}
	
	/**
	 * Recursive helper method to create encodings for each character using the
	 * Huffman trie
	 * 
	 * @param root      HuffNode that's the root of a subtree
	 * @param string    the path to root as a string of zeros and ones
	 * @param encodings the array of encodings
	 */
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		// this should never happen, but just in case
		if (root == null)
			return;

		// if root is a leaf, add the encoding
		if (root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path;

			// debug printing
			if (myDebugLevel >= DEBUG_HIGH) {
				System.out.printf("encoding for %d is %s\n", root.myValue, path);
			}
			return;
		}

		// otherwise, keep going
		codingHelper(root.myLeft, path + "0", encodings);
		codingHelper(root.myRight, path + "1", encodings);
	}

	/**
	 * Writes a pre-order traversal of the Huffman trie. Internal nodes are written
	 * as zero, leaf nodes are written as 1 followed by 9 bits of the value stored
	 * in the leaf
	 * 
	 * @param root the HuffNode of the trie that we are on
	 * @param out  the pre-order traversal of the trie
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
		HuffNode current = root;

		// leaf node
		if (current.myLeft == null && current.myRight == null) {
			out.writeBits(1, 1); // write a single bit of 1
			out.writeBits(BITS_PER_WORD + 1, current.myValue);

			// debug printing
			if (myDebugLevel >= DEBUG_HIGH) {
				System.out.printf("Wrote leaf for %d \n",current.myValue);
			}
		}
		// not a leaf
		else {
			out.writeBits(1, 0); // write a single bit of 0
			writeHeader(current.myLeft, out);
			writeHeader(current.myRight, out);
		}
	}
	
	/**
	 * Read file to be compressed and process one character at a time
	 * @param codings	a mapping of characters to encodings such that array[c] is the encoding of the 8-bit chunk c
	 * @param in	the file to be compressed
	 * @param out	where to write the compresed file
	 */
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) {
				String code = codings[PSEUDO_EOF];
				out.writeBits(code.length(), Integer.parseInt(code,2));
				break;
			}
			else {
				String code = codings[val];
				out.writeBits(code.length(), Integer.parseInt(code,2));
			}
		}	

	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in  Buffered bit stream of the file to be decompressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out) {
		// check on whether the file is Huffman-coded
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("Illegal header starts with " + bits);
		}
		// read the tree used to decompress
		HuffNode root = readTreeHeader(in);

		// read bits from compressed file, writing leaf values to the output file
		readCompressedBits(root, in, out);

		// close the output file
		out.close();
	}

	/**
	 * Read the bits from the compressed file and use them to traverse root-to-leaf
	 * paths, writing leaf values to the output file. Stop when PSEUDO_EOF is found.
	 * 
	 * @param root	the root HuffNode of the Huffman tree
	 * @param in	Buffered bit stream of the file to be decompressed.
	 * @param out	Buffered bit stream writing to the output file.
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			} else {
				if (bits == 0)
					current = current.myLeft;
				else
					current = current.myRight;
				// leaf node
				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF)
						break; // out of loop
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root; // start back after leaf
					}
				}
			}
		}

	}

	/**
	 * Recursively reads the tree stored using a pre-order traversal. Interior tree
	 * nodes are indicated by the single bit 0 and leaf nodes are indicated by the
	 * single bit 1. No values are written for internal nodes and a 9-bit value is
	 * written for a leaf node.
	 * 
	 * @param in	the stream of bits representing a pre-order traversal of the tree
	 * @return	a HuffNode that is the root node of this tree
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		// read a single bit
		int bit = in.readBits(1);

		// throw an exception if reading bits ever fails
		if (bit == -1) {
			throw new HuffException("Bit reading failed.");
		}
		// interior tree node
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0, 0, left, right);
		}
		// leaf node
		else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value, 0, null, null);
		}
	}
}