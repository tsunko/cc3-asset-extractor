package academy.hekiyou.okaerinasai;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.URL;
import static java.util.Map.Entry;

import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Application {
	
	public static final String BASE_URL = "https://akamai.chain-chronicle.net/Bdl56_And/";
	public static final String CORE_JSON_FILE_URL = BASE_URL + "files2.json";
	
	public static byte[] BASE_XOR_KEY = {
			0x55, 0x6E, 0x69, 0x74, 0x79, 0x46, 0x53, 0x00, 0x00, 0x00, 0x00
	};
	
	public static void main(String[] args){
		String json = readForeign(CORE_JSON_FILE_URL);
		Path savePath = Paths.get(".", "local").toAbsolutePath();
		JSONParser parser = new JSONParser();
		JSONObject root;
		try{
			root = (JSONObject)parser.parse(json);
		} catch (ParseException exc){
			exc.printStackTrace();
			return;
		}
		
		ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		JSONObject actualRoot = (JSONObject)root.get("files");
		Set<Entry> catagories = actualRoot.entrySet();
		for(Entry catagory : catagories){
			String base = (String)catagory.getKey();
			Set<Entry> filesInCat = ((JSONObject)catagory.getValue()).entrySet();
			for(Entry file : filesInCat){
				service.submit(() -> {
					String fileUrl = BASE_URL + base + "/" + file.getKey();
					Path folder = savePath.resolve(base);
					Path saveTo = folder.resolve(file.getKey().toString());
					System.out.println("Starting " + fileUrl + "...");
					if(!tryCreateParents(saveTo))
						return;
					saveForeign(fileUrl, (long)((JSONArray)file.getValue()).get(1), saveTo);
					System.out.println("Saved " + fileUrl + " to " + saveTo);
				});
			}
		}
		try{
			service.shutdown();
			service.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		} catch (InterruptedException exc){
			throw new RuntimeException(exc);
		}
	}
	
	public static boolean tryCreateParents(Path path){
		try {
			Path parent = path.getParent();
			if(Files.notExists(parent)){
				Files.createDirectories(parent);
			}
			return true;
		} catch (IOException exc){
			exc.printStackTrace();
			return false;
		}
	}
	
	public static void saveForeign(String url, long size, Path path){
		byte[] key = Arrays.copyOf(BASE_XOR_KEY, BASE_XOR_KEY.length);
		ByteBuffer buffer = ByteBuffer.allocate((int)size);
		ReadableByteChannel inChannel = null;
		FileOutputStream fos = null;
		try{
			inChannel = Channels.newChannel(new URL(url).openStream());
			inChannel.read(buffer);
			
			byte[] data = buffer.array();
			
			for (int i = 0; i < key.length; i++)
				key[i] ^= data[i];
			int len = data.length > 256 ? 256 : data.length;
			for (int i = 0; i < len; i++)
				data[i] ^= key[i % 11];
			
			fos = new FileOutputStream(path.toAbsolutePath().toFile());
			FileChannel fc = fos.getChannel();
			fc.write(ByteBuffer.wrap(data));
		} catch (IOException exc){
			throw new RuntimeException(exc);
		} finally {
			if(inChannel != null){
				try{
					inChannel.close();
				} catch (IOException exc){
					exc.printStackTrace();
				}
			}
			if(fos != null){
				try{
					fos.flush();
					fos.close();
				} catch (IOException exc){
					exc.printStackTrace();
				}
			}
		}
	}
	
	public static String readForeign(String url){
		BufferedReader reader = null;
		try {
			StringBuilder builder = new StringBuilder();
			reader = new BufferedReader(new InputStreamReader(new URL(url).openStream()));
			String line;
			
			while((line = reader.readLine()) != null)
				builder.append(line);
			
			return builder.toString();
		} catch (IOException exc){
			throw new RuntimeException(exc);
		} finally {
			if(reader != null){
				try {
					reader.close();
				} catch (IOException exc){
					exc.printStackTrace(); // something really bad?
				}
			}
		}
	}
	
}
