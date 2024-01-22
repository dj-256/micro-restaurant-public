import { ApiBody, ApiCreatedResponse, ApiTags } from '@nestjs/swagger';
import { Body, Controller, Post } from '@nestjs/common';
import { KitchenFacadeService } from '../../kitchenFacade/services/kitchen-facade.service';
import { RecipeDto } from '../dto/recipe.dto';

@ApiTags('Recipes')
@Controller('/recipes')
export class RecipesController {
  constructor(private readonly kitchenFacadeService: KitchenFacadeService) {}

  @ApiBody({ type: RecipeDto })
  @ApiCreatedResponse({ type: RecipeDto, description: 'The new recipe.' })
  @Post()
  addNewRecipe(@Body() recipeDto: RecipeDto) {
    return this.kitchenFacadeService.addNewRecipe(recipeDto);
  }
}
