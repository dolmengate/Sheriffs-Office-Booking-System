package DataAccess;

import info.sroman.SOBS.Entities.Prisoner;
import Models.PrisonerSearchModel;
import info.sroman.SOBS.Dao;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class PrisonerDAO extends Dao<Prisoner, PrisonerSearchModel> {
 
	private PrisonerSearchModel model;

	@Override
	public ArrayList<Prisoner> findAll(PrisonerSearchModel model) {
		
		// give utility methods access to search object
		this.model = model;

		ArrayList<Prisoner> prisoners = new ArrayList<>();

		try {
			conn = DriverManager.getConnection(
					"jdbc:sqlite:./src/main/resources/db/SOBS.db"
			);
			stmt = conn.createStatement();
			stmt.setQueryTimeout(10);
			ResultSet rs = stmt.executeQuery(constructSelectStatement());
			
			// Loop through the result set and create a new Prisoner entity
			// for each of the matching rows
			while (rs.next()) {
				Prisoner p = new Prisoner(
						rs.getInt("PERSON_ID"), rs.getString("first_name"),
						rs.getString("last_name"), rs.getInt("height"),
						rs.getInt("weight"), rs.getString("date_of_birth"),
						rs.getString("race"), rs.getInt("PRISONER_ID"),
						rs.getString("arrest_date"), rs.getString("release_date"),
						rs.getInt("bunk_ID"), (rs.getInt("is_released") == 1)
				);

				// include the Prisoner's cell block type
				p.setCellBlock(rs.getString("type"));

				prisoners.add(p);
			}

		} catch (SQLException ex) {
			System.err.println(ex.getMessage());
		} finally {
			try {
				if (conn != null) {
					conn.close();
				}
			} catch (SQLException ex) {
				System.err.println(ex);
			}
		}
		return prisoners;
	}
	
	@Override
	public boolean create(PrisonerSearchModel model) {
		
		this.model = model;
		boolean success = true;
		
	/*
		Person and Prisoner must be joined to update the Prisoner record completely
		SQlite 3.2 does not support WITH clauses, therefore I am forced to update
		each table in a separate statement.
	*/
		
		try {
			conn = DriverManager.getConnection(
					"jdbc:sqlite:./src/main/resources/db/SOBS.db"
			);
			conn.setAutoCommit(false);

			prepStmt = conn.prepareStatement(
					"INSERT INTO Person VALUES (?, ?, ?, ?, ?, ?, ?)"
			);
			prepStmt.setInt(1, Integer.valueOf(model.getPersonId()));
			prepStmt.setString(2, model.getFirstName());
			prepStmt.setString(3, model.getLastName());
			prepStmt.setInt(4, Integer.valueOf(model.getHeight()));
			prepStmt.setInt(5, Integer.valueOf(model.getWeight()));
			prepStmt.setString(6, model.getDob());
			prepStmt.setString(7, model.getRace());
			prepStmt.executeUpdate();
			conn.commit();

			prepStmt = conn.prepareStatement(
					"INSERT INTO Prisoner VALUES (?, ?, ?, ?, ?, ?)"
			);
			prepStmt.setInt(1, Integer.valueOf(model.getPrisonerId()));
			prepStmt.setString(2, model.getArrestDate());
			prepStmt.setString(3, model.getReleaseDate());
			prepStmt.setInt(4, Integer.valueOf(model.getPersonId()));
			prepStmt.setInt(5, Integer.valueOf(model.getBunkId()));
			prepStmt.setInt(6, (model.isReleased() ? 1 : 0));
			prepStmt.executeUpdate();
			conn.commit();

		} catch (SQLException ex) {
			System.err.println(ex);
			try {
				prepStmt.cancel();
			} catch (SQLException cancelEx) {
				System.err.println(cancelEx);
				System.err.println("Unable to cancel executed statement.");
			}
			success = false;
		} finally {
			try {
				if (conn != null) {
					conn.close();
				}
			} catch (SQLException ex) {
				System.err.println(ex);
			}
		}
		return success;
	}

	@Override
	public boolean update(PrisonerSearchModel model) {
		
		// give utility methods constructPersonUpdateStatement and 
		// constructPrisonerUpdateStatement access to the model to create query
		this.model = model;

		boolean success = true;

		try {
			conn = DriverManager.getConnection(
					"jdbc:sqlite:./src/main/resources/db/SOBS.db"
			);

			stmt = conn.createStatement();
			stmt.setQueryTimeout(10);
			stmt.executeUpdate(constructPersonUpdateStatement());
			stmt.executeUpdate(constructPrisonerUpdateStatement());

		} catch (SQLException ex) {
			System.err.println(ex.getMessage());
			try {
				stmt.cancel();
			} catch (SQLException cancelEx) {
				System.err.println(cancelEx);
				System.err.println("Unable to cancel executed statement.");
			}
			success = false;
		} finally {
			try {
				if (conn != null) {
					conn.close();
				}
			} catch (SQLException ex) {
				System.err.println(ex);
			}
		}
		return success;
	}

	@Override
	public boolean delete(PrisonerSearchModel model) {
		
		boolean success = true;
		
		try {
			conn = DriverManager.getConnection(
					"jdbc:sqlite:./src/main/resources/db/SOBS.db"
			);

			stmt = conn.createStatement();
			stmt.setQueryTimeout(10);
			stmt.executeUpdate(
					"UPDATE Prisoner SET is_released = 1 WHERE PRISONER_ID = '" + model.getPrisonerId()+ "'"
			);
			System.out.println("UPDATE Prisoner SET is_released = 1 WHERE PRISONER_ID = '" + model.getPrisonerId()+ "'");

		} catch (SQLException ex) {
			System.err.println(ex.getMessage());
			success = false;
			try {
				stmt.cancel();
			} catch (SQLException cancelEx) {
				System.err.println(cancelEx);
				System.err.println("Unable to cancel executed statement.");
			}
		} finally {
			try {
				if (conn != null) {
					conn.close();
				}
			} catch (SQLException ex) {
				System.err.println(ex);
			}
		}
		return success;
	}

	/**
	 * Constructs the  SELECT statement for use when a user searches
	 * for a prisoner.
	 * @return 
	 */
	private String constructSelectStatement() {

		// Perform table joins mandatory for any search
		StringBuilder baseStatement = new StringBuilder(
				"SELECT * FROM Person "
				+ "LEFT JOIN "
				+ "Prisoner ON Person.PERSON_ID = Prisoner.PERSON_ID "
				+ "LEFT JOIN "
				+ "Bunk ON Prisoner.bunk_id = Bunk.BUNK_ID "
				+ "LEFT JOIN "
				+ "Cell ON Bunk.cell_id = Cell.CELL_ID "
		);

		// parallel arrays that associate a TextField with its relevant table column
		String[] fieldValues = {
			model.getPersonId(),
			model.getFirstName(),
			model.getLastName(),
			model.getHeight(),
			model.getWeight(),
			model.getDob(),
			model.getRace(),
			model.getPrisonerId(),
			model.getArrestDate(),
			model.getArrestDate(),
			model.getBunkId(),
			model.getCellType()
		};

		String[] columns = {
			"PERSON_ID", "first_name", "last_name", "height", "weight", "date_of_birth",
			"race", "PRISONER_ID", "arrest_date", "release_date", "bunk_id", "Cell.type"
		};

		// complete the statement if the user entered no search criteria
		if (fieldsAreEmpty(fieldValues)) {
			System.out.println(baseStatement.toString());
			return baseStatement.toString();
		}
		
		baseStatement.append("WHERE ");
		baseStatement.append(constructWhereClauses(fieldValues, columns));

		// Prevent "Ambugious column" error by adding Table name
		if (!model.getPersonId().equals("")) {
			baseStatement.insert(baseStatement.indexOf(" PERSON_ID ") + 1, "Person.", 0, 7);
		}

		// only ever return Prisoners not released
		baseStatement.append(" AND is_released = '0' ");

		System.out.println(baseStatement);

		return baseStatement.toString();
	}

	/**
	 * Constructs the UPDATE statement for the Person table upon a user modifying
	 * a Prisoner's information.
	 * @return 
	 */
	private String constructPersonUpdateStatement() {
		StringBuilder baseStatement = new StringBuilder(
				"UPDATE Person SET "
		);

		String[] personValues = {
			model.getPersonId(),
			model.getFirstName(),
			model.getLastName(),
			model.getHeight(),
			model.getWeight(),
			model.getDob(),
			model.getRace()
		};

		String[] personColumns = {
			"PERSON_ID", "first_name", "last_name", "height", "weight", "date_of_birth",
			"race"
		};

		for (int i = 0; i < personValues.length; i++) {
			baseStatement.append(personColumns[i]).append(" = '").append(personValues[i]).append("'");
			if (i != personValues.length - 1) {
				baseStatement.append(", ");
			}
		}

		baseStatement.append(" WHERE Person.PERSON_ID = ").append(model.getPersonId());

		System.out.println(baseStatement.toString());

		return baseStatement.toString();
	}

	/**
	 * Constructs the UPDATE statement for the Prisoner table upon a user
	 * modifying a Prisoner's information.
	 * @return 
	 */
	private String constructPrisonerUpdateStatement() {
		StringBuilder baseStatement = new StringBuilder(
				"UPDATE Prisoner SET "
		);

		String[] prisonerValues = {
			model.getPrisonerId(),
			model.getArrestDate(),
			model.getReleaseDate(),
			model.getBunkId()
		};
		String[] prisonerColumns = {
			"PRISONER_ID",
			"arrest_date",
			"release_date",
			"bunk_id"
		};

		for (int i = 0; i < prisonerValues.length; i++) {
			baseStatement.append(prisonerColumns[i]).append(" = '").append(prisonerValues[i]).append("'");
			if (i != prisonerValues.length - 1) {
				baseStatement.append(", ");
			}
		}

		baseStatement.append(" WHERE Prisoner.PRISONER_ID = ").append(model.getPrisonerId());

		System.out.println(baseStatement.toString());

		return baseStatement.toString();
	}
}
