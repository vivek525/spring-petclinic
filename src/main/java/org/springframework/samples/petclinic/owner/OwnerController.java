/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import java.util.List;
import java.util.Map;

import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Michael Isvy
 */
@Controller
class OwnerController {

	private static final String VIEWS_OWNER_CREATE_OR_UPDATE_FORM = "owners/createOrUpdateOwnerForm";

	private final OwnerRepository owners;

	public OwnerController(OwnerRepository clinicService) {
		this.owners = clinicService;
	}

	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id");
	}
    /* https://www.baeldung.com/spring-mvc-and-the-modelattribute-annotation
     * When we use the annotation at the method level, it indicates the purpose of the method is to add one or more model attributes.
     * @ModelAttribute methods are invoked before the controller methods annotated with @RequestMapping are invoked. This call to the @ModelAttribute annotated method
     * happens before each call to handler methods
     * When we use the annotation as a method argument, it indicates to retrieve the argument from the model. 
     * Ex public String submit(@ModelAttribute("employee") Employee employee). 
     * A lookup of the string employee happens against the Model object and the correspondng Employee object is returned 
     *      
     *      */
	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable(name = "ownerId", required = false) Integer ownerId) {
		return ownerId == null ? new Owner() : this.owners.findById(ownerId);
	}

	@GetMapping("/owners/new")
	public String initCreationForm(Map<String, Object> model) {
		Owner owner = new Owner();
		model.put("owner", owner);
		/*
		 *  A String return value by default refers to a view name. If you want it to be the response then add the @ResponseBody annotation
		 *  the @ResponseBody annotation tells the controller that the object returned should be automatically serialized to the configured media type
		 *  @GetMapping(value = "/welcome", produces = MediaType.TEXT_HTML_VALUE)
		 *  @ResponseBody. Reference https://www.baeldung.com/spring-mvc-return-html
		 */
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/owners/new")
	public String processCreationForm(@Valid Owner owner, BindingResult result) {
		if (result.hasErrors()) {
			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}
		else {
			/*
			 * It is sometimes desirable to issue an HTTP redirect back to the client, before the view is rendered. 
			 * This is desirable for example when one controller has been called with POSTed data, and the response is actually a 
			 * delegation to another controller (for example on a successful form submission). 
			 * In this case, a normal internal forward will mean the other controller will also see the same POST data, 
			 * which is potentially problematic if it can confuse it with other expected data. Another reason to do a redirect before 
			 * displaying the result is that this will eliminate the possibility of the user doing a double submission of form data. 
			 * The browser will have sent the initial POST, will have seen a redirect back and done a subsequent GET because of that, 
			 * and thus as far as it is concerned, the current page does not reflect the result of a POST, 
			 * but rather of a GET, so there is no way the user can accidentally re-POST the same data by doing a refresh. 
			 * The refresh forces a GET of the result page, not a resend of the initial POST data.
			 */
			this.owners.save(owner);
			return "redirect:/owners/" + owner.getId();
		}
	}

	@GetMapping("/owners/find")
	public String initFindForm(Map<String, Object> model) {
		model.put("owner", new Owner());
		/*
		 * Thymeleaf is the template engine used in the PetClinic application.As with many things, 
		 * Spring Boot provides a default location where it expects to find our templates.By default, Spring Boot looks for our templates in src/main/resources/templates. We can put our templates there and 
		 * organize them in sub-directories. https://www.baeldung.com/spring-thymeleaf-template-directory 
		 */
		return "owners/findOwners";
	}

	@GetMapping("/owners")
	public String processFindForm(@RequestParam(defaultValue = "1") int page, Owner owner, BindingResult result,
			Model model) {
		/* Model interface is available in the org.springframework.ui package. It acts as a data container that contains the data of the application. 
		 * That stored data can be of any form such as String, Object, data from the Database, etc.
		 * https://www.geeksforgeeks.org/spring-mvc-model-interface/ 
		 */

		// allow parameterless GET request for /owners to return all records
		if (owner.getLastName() == null) {
			owner.setLastName(""); // empty string signifies broadest possible search
		}

		// find owners by last name
		Page<Owner> ownersResults = findPaginatedForOwnersLastName(page, owner.getLastName());
		if (ownersResults.isEmpty()) {
			// no owners found
			result.rejectValue("lastName", "notFound", "not found");
			return "owners/findOwners";
		}
		else if (ownersResults.getTotalElements() == 1) {
			// 1 owner found
			owner = ownersResults.iterator().next();
			return "redirect:/owners/" + owner.getId();
		}
		else {
			// multiple owners found
			return addPaginationModel(page, model, ownersResults);
		}
	}

	private String addPaginationModel(int page, Model model, Page<Owner> paginated) {
		model.addAttribute("listOwners", paginated);
		List<Owner> listOwners = paginated.getContent();
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", paginated.getTotalPages());
		model.addAttribute("totalItems", paginated.getTotalElements());
		model.addAttribute("listOwners", listOwners);
		return "owners/ownersList";
	}

	private Page<Owner> findPaginatedForOwnersLastName(int page, String lastname) {

		int pageSize = 5;
		Pageable pageable = PageRequest.of(page - 1, pageSize);
		return owners.findByLastName(lastname, pageable);

	}

	/*
	 * https://dzone.com/articles/spring-boot-passing-parameters
	 * Passing parameters when requesting URLs is one of the most basic features in Spring Boot.There are two ways to pass parameters
	 * 1. Request Parameter https://www.youtube.com/watch?v=tuNhqqxgxXQ&t=153s In the above URL, there are two parameters which are v and t. To pass the parameters, put “?”. Then, add the parameter name followed by “=” and the value of the parameter. It will look like this “?v=value”. 
	 * To pass another, use “&” followed by the second parameter as the first one.
	 * 2. Path Variable - http://www.twitter.com/hashimati. The word “hashimati” in this URL is a variable.
	 */
	
	/*
	 * Path Variable can be handled as in the example below. For Request Parameter there are 2 options
	 * Option 1 - public String hello1(@RequestParam(name="name", required = false, defaultValue = "Ahmed") String name)--Call out the request param in method signature
	 * Option 2 - public String hello2(Parms parameters) -- Create a domain object and encapsulate the parameters in an object, Spring will do the binding for you
	 */
	@GetMapping("/owners/{ownerId}/edit")
	public String initUpdateOwnerForm(@PathVariable("ownerId") int ownerId, Model model) {
		/*
		 * @GetMapping or @RequestMapping annotation can be used to construct the dynamic or the run-time URI i.e. to pass in the parameters. 
		 * This can be achieved by using the @PathVariable
		 */
		Owner owner = this.owners.findById(ownerId);
		model.addAttribute(owner);
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/owners/{ownerId}/edit")
	public String processUpdateOwnerForm(@Valid Owner owner, BindingResult result,
			@PathVariable("ownerId") int ownerId) {
		if (result.hasErrors()) {
			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}
		else {
			owner.setId(ownerId);
			this.owners.save(owner);
			return "redirect:/owners/{ownerId}";
		}
	}

	/**
	 * Custom handler for displaying an owner.
	 * @param ownerId the ID of the owner to display
	 * @return a ModelMap with the model attributes for the view
	 */
	@GetMapping("/owners/{ownerId}")
	public ModelAndView showOwner(@PathVariable("ownerId") int ownerId) {
		ModelAndView mav = new ModelAndView("owners/ownerDetails");
		Owner owner = this.owners.findById(ownerId);
		mav.addObject(owner);
		return mav;
	}

}

/*
https://www.javacodegeeks.com/2018/06/domain-objects-spring-mvc.html
https://docs.spring.io/spring-framework/docs/current/reference/html/web.html#mvc-ann-modelattrib-method-args
*/