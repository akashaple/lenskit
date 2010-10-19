/* RefLens, a reference implementation of recommender algorithms.
 * Copyright 2010 Michael Ekstrand <ekstrand@cs.umn.edu>
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package org.grouplens.reflens.bench;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.grouplens.reflens.RatingPredictor;
import org.grouplens.reflens.RecommendationEngine;
import org.grouplens.reflens.RecommenderBuilder;
import org.grouplens.reflens.data.ScoredObject;
import org.grouplens.reflens.data.UserRatingProfile;
import org.grouplens.reflens.util.CollectionDataSource;
import org.grouplens.reflens.util.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to aggregate benchmarking runs.
 * @author Michael Ekstrand <ekstrand@cs.umn.edu>
 *
 */
public class BenchmarkAggregator {
	private static Logger logger = LoggerFactory.getLogger(BenchmarkAggregator.class);
	private RecommenderBuilder<Integer,Integer> factory;
	private int numRuns = 0;
	private float tMAE = 0.0f;
	private float tRMSE = 0.0f;
	private float tCov = 0.0f;
	private double holdout = 0.33333333;
	
	public BenchmarkAggregator(RecommenderBuilder<Integer,Integer> factory) {
		this.factory = factory;
	}
	
	public double holdoutFraction() {
		return holdout;
	}
	public void setHoldoutFraction(double fraction) {
		if (fraction <= 0 || fraction >= 1) {
			throw new RuntimeException("Invalid holdout fraction");
		}
		holdout = fraction;
	}
	
	/**
	 * Run the benchmarker over a train/test set and record the results.
	 * @param trainUsers The set of users for building the model.
	 * @param testUsers The set of users for testing the resulting model.  Users
	 * are tested by holding back 1/3 of their ratings.
	 */
	public void addBenchmark(
			Collection<UserRatingProfile<Integer,Integer>> trainUsers,
			Collection<UserRatingProfile<Integer,Integer>> testUsers) {
		logger.debug("Building model with {} users", trainUsers.size());
		DataSource<UserRatingProfile<Integer, Integer>> trainingSource =
			new CollectionDataSource<UserRatingProfile<Integer,Integer>>(trainUsers);
		RecommendationEngine<Integer, Integer> engine;
		try {
			engine = factory.build(trainingSource);
		} finally {
			trainingSource.close();
		}
		RatingPredictor<Integer, Integer> rec = engine.getRatingPredictor();
		
		logger.debug("Testing model with {} users", testUsers.size());
		float accumErr = 0.0f;
		float accumSqErr = 0.0f;
		int nitems = 0;
		int ngood = 0;
		for (UserRatingProfile<Integer,Integer> user: testUsers) {
			List<ScoredObject<Integer>> ratings = new ArrayList<ScoredObject<Integer>>(
					ScoredObject.wrap(user.getRatings()));
			int midpt = (int) Math.round(ratings.size() * (1.0 - holdout));
			// TODO: make this support timestamped ratings
			Collections.shuffle(ratings);
			Int2FloatMap queryRatings = new Int2FloatOpenHashMap();
			for (int i = 0; i < midpt; i++) {
				ScoredObject<Integer> rating = ratings.get(i);
				queryRatings.put((int) rating.getObject(), rating.getScore());
			}
			for (int i = midpt; i < ratings.size(); i++) {
				int iid = ratings.get(i).getObject();
				ScoredObject<Integer> prediction = rec.predict(user.getUser(), queryRatings, iid);
				nitems++;
				if (prediction != null) {
					float err = prediction.getScore() - user.getRating(iid);
					ngood++;
					accumErr += Math.abs(err);
					accumSqErr += err * err;
				}
			}
		}
		
		System.out.format("Finished run. MAE=%f, RMSE=%f, coverage=%d/%d\n",
				accumErr / ngood, Math.sqrt(accumSqErr / ngood), ngood, nitems);
		numRuns++;
		tMAE += accumErr / ngood;
		tRMSE += Math.sqrt(accumSqErr / ngood);
		tCov += (float) ngood / nitems;
	}
	
	public void printResults() {
		System.out.format("Ran %d folds.\n", numRuns);
		System.out.format("Average MAE: %f\n", tMAE / numRuns);
		System.out.format("Average RMSE: %f\n", tRMSE / numRuns);
		System.out.format("Average coverage: %f\n", tCov / numRuns);
	}
}
