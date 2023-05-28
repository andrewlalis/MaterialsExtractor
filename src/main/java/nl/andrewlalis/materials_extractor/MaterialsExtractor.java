package nl.andrewlalis.materials_extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MaterialsExtractor {
	private static final Map<String, String> CONVERSIONS = new HashMap<>();
	static {
		CONVERSIONS.put("minecraft:redstone_wall_torch", "minecraft:redstone_torch");
		CONVERSIONS.put("minecraft:piston_head", null);
		CONVERSIONS.put("minecraft:redstone_wire", "minecraft:redstone");
	}

	private static final ObjectMapper MAPPER = new ObjectMapper();

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println("Missing required NBT file as 1st argument to this program.");
			System.exit(1);
		}
		Path nbtFilePath = Path.of(args[0]);
		if (Files.notExists(nbtFilePath) || !Files.isReadable(nbtFilePath) || !nbtFilePath.getFileName().toString().endsWith(".nbt")) {
			System.out.println("Invalid or missing file: " + args[0]);
			System.exit(1);
		}
		final var counts = getItemList(nbtFilePath);

		ObjectNode obj = MAPPER.createObjectNode();
		counts.keySet().stream().sorted().forEachOrdered(key -> obj.put(key, counts.get(key)));
		String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
		if (args.length >= 2) {
			// Use 2nd arg as api key for paste.ee.
			upload(json, args[1]);
		} else {
			System.out.println("Copy and paste the following JSON to a pastebin service, and copy the \"raw\" download URL when running the item-extractor program on your in-game PC:");
			System.out.println(json);
		}
	}

	private static Map<String, Integer> getItemList(Path filePath) throws IOException {
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
			String converted = CONVERSIONS.getOrDefault(name, name);
			if (converted != null) {
				counts.put(converted, 1 + counts.computeIfAbsent(converted, s -> 0));
			}
		}
		return counts;
	}

	private static void upload(String content, String apiToken) throws Exception {
		HttpClient httpClient = HttpClient.newHttpClient();

		ObjectNode body = MAPPER.createObjectNode();
		body.put("encrypted", false);
		body.put("description", "Schematic item-list");
		ObjectNode section = MAPPER.createObjectNode();
		section.put("name", "item-list.json");
		section.put("syntax", "json");
		section.put("contents", content);
		body.set("sections", MAPPER.createArrayNode().add(section));


		HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.paste.ee/v1/pastes"))
				.POST(HttpRequest.BodyPublishers.ofString(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body)))
				.header("X-Auth-Token", apiToken)
				.header("Content-Type", "application/json")
				.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() >= 400) {
			System.err.println("Error: " + response.statusCode());
			return;
		}
		ObjectNode responseBody = MAPPER.readValue(response.body(), ObjectNode.class);
		String pasteId = responseBody.get("id").asText();
		System.out.println("https://paste.ee/d/" + pasteId);
	}
}
