package nl.andrewlalis.materials_extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MaterialsExtractor {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println("Missing at least one NBT file as an argument to the program.");
			System.exit(1);
		}
		for (String arg : args) {
			if (!validateInputFile(arg)) {
				System.out.println("Invalid or missing file: " + arg);
				System.exit(1);
			}
		}
		var conversions = parseConversions();
		ArrayNode array = MAPPER.createArrayNode();
		for (String arg : args) {
			array.add(extractFromFile(arg, conversions));
		}
		ObjectMapper outputMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		String json = outputMapper.writeValueAsString(array);
		System.out.println(json);
	}

	private static boolean validateInputFile(String file) {
		Path filePath = Path.of(file);
		return Files.exists(filePath) &&
				Files.isReadable(filePath) &&
				filePath.getFileName().toString().toLowerCase().endsWith(".nbt");
	}

	private static Map<String, String> parseConversions() throws IOException {
		try (var in = MaterialsExtractor.class.getResourceAsStream("/conversions.json")) {
			if (in == null) throw new IOException("Couldn't find resource.");
			ObjectNode obj = MAPPER.readValue(in, ObjectNode.class);
			Map<String, String> conversions = new HashMap<>();
			for (var entry : obj.properties()) {
				if (entry.getValue().isNull()) {
					conversions.put(entry.getKey(), null);
				} else {
					conversions.put(entry.getKey(), entry.getValue().asText());
				}
			}
			return Collections.unmodifiableMap(conversions);
		}
	}

	private static ObjectNode extractFromFile(String file, Map<String, String> conversions) throws IOException {
		var filePath = Path.of(file);
		var counts = getItemList(filePath, conversions);
		var obj = countsToJson(counts);
		obj.put("__NAME__", filePath.getFileName().toString());
		obj.put("__TIMESTAMP__", LocalDateTime.now(ZoneOffset.UTC).toString());
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] hash = md.digest(Files.readAllBytes(filePath));
			StringBuilder sb = new StringBuilder(hash.length * 2);
			for (byte b : hash) sb.append(String.format("%02x", b));
			obj.put("__SHA1_HASH__", sb.toString());
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		return obj;
	}

	/**
	 * Gets a mapping of items to their required count, for a Create-mod
	 * NBT schematic file. The NBT is structured such that it contains a
	 * <code>palette</code> list of compound tags, and a <code>blocks</code>
	 * list of compound tags. We first parse an array of all item names from the
	 * palette, then look through the blocks to count up the instances of each.
	 * @param filePath The file path to read.
	 * @param conversions A mapping for item names to convert to others (or null)
	 *                    in order to avoid weird minecraft items like piston
	 *                    heads.
	 * @return A mapping of item names to their count.
	 * @throws IOException If a read error occurs.
	 */
	private static Map<String, Integer> getItemList(Path filePath, Map<String, String> conversions) throws IOException {
		NamedTag root = NBTUtil.read(filePath.toFile());
		CompoundTag compoundTag = (CompoundTag) root.getTag();
		ListTag<CompoundTag> paletteEntries = compoundTag.getListTag("palette").asCompoundTagList();
		String[] palette = new String[paletteEntries.size()];
		int idx = 0;
		for (var entry : paletteEntries) {
			palette[idx++] = entry.getString("Name");
		}

		ListTag<CompoundTag> blockEntries = compoundTag.getListTag("blocks").asCompoundTagList();
		Map<String, Integer> counts = new HashMap<>();
		for (var entry : blockEntries) {
			String name = palette[entry.getInt("state")];
			String converted = conversions.getOrDefault(name, name);
			if (converted != null) {
				counts.put(converted, 1 + counts.computeIfAbsent(converted, s -> 0));
			}
		}
		return counts;
	}

	private static ObjectNode countsToJson(Map<String, Integer> counts) {
		ObjectNode obj = MAPPER.createObjectNode();
		counts.keySet().stream().sorted().forEachOrdered(key -> obj.put(key, counts.get(key)));
		return obj;
	}
}
