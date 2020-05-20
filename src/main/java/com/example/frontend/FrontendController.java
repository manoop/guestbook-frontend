package com.example.frontend;

import org.springframework.stereotype.Controller;
import org.springframework.web.client.RestTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.*;
import java.util.*;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gcp.pubsub.core.*;

import org.springframework.cloud.gcp.core.GcpProjectIdProvider;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.WritableResource;
import org.springframework.util.StreamUtils;
import java.io.*;

@RefreshScope
@Controller
@SessionAttributes("name")
public class FrontendController {
	@Autowired
	private GuestbookMessagesClient client;

/*	@Autowired
	private PubSubTemplate pubSubTemplate;*/

	@Autowired
	private OutboundGateway outboundGateway;

	@Value("${greeting:Hello}")
	private String greeting;

	// The ApplicationContext is needed to create a new Resource.
	@Autowired
	private ApplicationContext context;

	// Get the Project ID, as its Cloud Storage bucket name here
	@Autowired
	private GcpProjectIdProvider projectIdProvider;

	@GetMapping("/")
	public String index(Model model) {
		if (model.containsAttribute("name")) {
			String name = (String) model.asMap().get("name");
			model.addAttribute("greeting", String.format("%s %s", greeting, name));
		}
		model.addAttribute("messages", client.getMessages().getContent());
		return "index";
	}
	
	@PostMapping("/post")
	public String post(
			@RequestParam(name="file", required=false) MultipartFile file,
			@RequestParam String name,
			@RequestParam String message, Model model)
			throws IOException {
		model.addAttribute("name", name);

		String filename = null;
		if (file != null && !file.isEmpty()
				&& file.getContentType().equals("image/jpeg")) {
			// Bucket ID is our Project ID
			String bucket = "gs://" +
					projectIdProvider.getProjectId();
			// Generate a random file name
			filename = UUID.randomUUID().toString() + ".jpg";
			WritableResource resource = (WritableResource)
					context.getResource(bucket + "/" + filename);
			// Write the file to Cloud Storage
			try (OutputStream os = resource.getOutputStream()) {
				os.write(file.getBytes());
			}
		}

		if (message != null && !message.trim().isEmpty()) {
//			pubSubTemplate.publish("messages", name + ": " + message);
			// Post the message to the backend service
			Map<String, String> payload = new HashMap<>();
			payload.put("name", name);
			payload.put("message", message);
			// Store the generated file name in the database
			payload.put("imageUri", filename);
			client.add(payload);
			outboundGateway.publishMessage(name + ": " + message);
		}
		return "redirect:/";
  }
}

