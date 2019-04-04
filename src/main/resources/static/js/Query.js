const input = document.querySelectorAll("input[type=text]");
const reset = document.querySelector("#reset");
const submit = document.querySelector("#querySbmt");
const form = document.querySelector("form");
const locDiv = document.querySelector("#locDiv");
const locationInput = document.querySelector("#location");
const distanceInput = document.querySelector("#distance");



reset.addEventListener('click',function(){

	Object.keys(input).forEach(function(key) {
    input[key].value='';

});

});

submit.addEventListener('click',function(e){

  if(locationInput.value == ''|| distanceInput.value==''){
    createErrorDiv();
  }
  else{
    createSuccessDiv();
  }

});

function createSuccessDiv(){
  const successDiv = document.createElement("div");
  const successText = document.createTextNode("Successfully Added");
  successDiv.appendChild(successText);
  successDiv.classList.add('successColor');
  form.insertBefore(successDiv,locDiv);
  deleteDiv(successDiv);
}

function createErrorDiv(){
  const errorDiv = document.createElement("div");
  const errorText = document.createTextNode("Please fill all fields");
  errorDiv.appendChild(errorText);
  errorDiv.classList.add('errorColor');
  form.insertBefore(errorDiv,locDiv);
  deleteDiv(errorDiv);

}

function deleteDiv(div){
  setTimeout(function(){
    div.remove();
  },1000);
}